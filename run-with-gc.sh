#!/bin/bash

# Enhanced Script to compare memory consumption with different garbage collectors
# Requires JDK 25 with preview features enabled
# Results are saved to log directory for analysis

# export JAVA_HOME=$HOME/zoo/jdk-25.jdk/Contents/Home
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
export MAVEN_OPTS="--add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED"

MAIN_CLASS="org.feuyeux.jdk25.language.ModuleImport"
CP="target/classes"
LOG_DIR="log"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
SUMMARY_FILE="$LOG_DIR/memory_comparison_$TIMESTAMP.txt"

echo "=================== GC Memory Consumption Comparison ==================="
echo "JAVA_HOME: $JAVA_HOME"
echo "Main Class: $MAIN_CLASS"
echo "Log Directory: $LOG_DIR"
echo "Timestamp: $TIMESTAMP"
echo "========================================================================"

# Create log directory
echo
echo "[INFO] Setting up log directory..."
mkdir -p "$LOG_DIR"
if [ ! -d "$LOG_DIR" ]; then
    echo "[ERROR] Failed to create log directory '$LOG_DIR'"
    exit 1
fi

# Compile the project first
echo
echo "[INFO] Compiling project..."
mvn compile -q 2>&1 | grep -v "WARNING.*sun.misc.Unsafe\|WARNING.*staticFieldBase\|WARNING.*HiddenClassDefiner" || true

if [ ! -d "$CP" ]; then
    echo "[ERROR] Classpath directory '$CP' not found. Compilation may have failed."
    exit 1
fi

if [ ! -f "$JAVA_HOME/bin/java" ]; then
    echo "[ERROR] Java executable not found at $JAVA_HOME/bin/java"
    exit 1
fi

# Initialize summary file
echo "GC Memory Consumption Comparison Report" > "$SUMMARY_FILE"
echo "Generated on: $(date)" >> "$SUMMARY_FILE"
echo "JDK Version: $($JAVA_HOME/bin/java --version | head -1)" >> "$SUMMARY_FILE"
echo "========================================" >> "$SUMMARY_FILE"
echo >> "$SUMMARY_FILE"

# Arrays to store results for markdown table generation
declare -a GC_NAMES
declare -a EXECUTION_TIMES
declare -a HEAP_USED
declare -a HEAP_COMMITTED
declare -a HEAP_RESERVED
declare -a GC_COUNTS
declare -a METASPACE_USED
declare -a METASPACE_COMMITTED
declare -a REGION_SIZES
declare -a STATUS_RESULTS

# Function to extract comprehensive memory usage from GC log
extract_memory_stats() {
    local log_file="$1"
    local gc_name="$2"
    local execution_time="$3"
    
    if [ -f "$log_file" ]; then
        # Extract heap statistics from exit information - try multiple patterns for different GC types
        local heap_used="N/A"
        local heap_committed="N/A"
        local heap_reserved="N/A"
        local region_size="N/A"
        
        # Try G1GC pattern first
        local heap_info=$(grep "total reserved" "$log_file" | tail -1)
        if [[ "$heap_info" =~ total\ reserved\ ([0-9]+)K,\ committed\ ([0-9]+)K,\ used\ ([0-9]+)K ]]; then
            heap_reserved="${BASH_REMATCH[1]}K"
            heap_committed="${BASH_REMATCH[2]}K"
            heap_used="${BASH_REMATCH[3]}K"
        else
            # Try Serial/Parallel GC patterns - sum up memory from different regions
            local defnew_used=$(grep "DefNew.*total.*used" "$log_file" | tail -1 | grep -o "used [0-9]*K" | grep -o "[0-9]*")
            local tenured_used=$(grep "Tenured.*total.*used" "$log_file" | tail -1 | grep -o "used [0-9]*K" | grep -o "[0-9]*")
            local defnew_total=$(grep "DefNew.*total.*used" "$log_file" | tail -1 | grep -o "total [0-9]*K" | grep -o "[0-9]*")
            local tenured_total=$(grep "Tenured.*total.*used" "$log_file" | tail -1 | grep -o "total [0-9]*K" | grep -o "[0-9]*")
            
            # Try Parallel GC pattern (PSYoungGen, ParOldGen)
            if [ -z "$defnew_used" ]; then
                defnew_used=$(grep "PSYoungGen.*total.*used" "$log_file" | tail -1 | grep -o "used [0-9]*K" | grep -o "[0-9]*")
                tenured_used=$(grep "ParOldGen.*total.*used" "$log_file" | tail -1 | grep -o "used [0-9]*K" | grep -o "[0-9]*")
                defnew_total=$(grep "PSYoungGen.*total.*used" "$log_file" | tail -1 | grep -o "total [0-9]*K" | grep -o "[0-9]*")
                tenured_total=$(grep "ParOldGen.*total.*used" "$log_file" | tail -1 | grep -o "total [0-9]*K" | grep -o "[0-9]*")
            fi
            
            # Calculate totals if we found the data
            if [[ "$defnew_used" =~ ^[0-9]+$ ]] && [[ "$tenured_used" =~ ^[0-9]+$ ]]; then
                heap_used="$((defnew_used + tenured_used))K"
            fi
            if [[ "$defnew_total" =~ ^[0-9]+$ ]] && [[ "$tenured_total" =~ ^[0-9]+$ ]]; then
                heap_committed="$((defnew_total + tenured_total))K"
                heap_reserved="$heap_committed"  # For these GCs, committed is usually close to reserved
            fi
            
            # Try ZGC pattern
            if [ "$heap_used" = "N/A" ]; then
                local zgc_used=$(grep -o "used [0-9]*M" "$log_file" | tail -1 | grep -o "[0-9]*")
                local zgc_committed=$(grep -o "committed [0-9]*M" "$log_file" | tail -1 | grep -o "[0-9]*")
                local zgc_reserved=$(grep -o "reserved [0-9]*M" "$log_file" | tail -1 | grep -o "[0-9]*")
                
                if [[ "$zgc_used" =~ ^[0-9]+$ ]]; then
                    heap_used="$((zgc_used * 1024))K"
                fi
                if [[ "$zgc_committed" =~ ^[0-9]+$ ]]; then
                    heap_committed="$((zgc_committed * 1024))K"
                fi
                if [[ "$zgc_reserved" =~ ^[0-9]+$ ]]; then
                    heap_reserved="$((zgc_reserved * 1024))K"
                fi
            fi
        fi
        
        # Extract region size for applicable GCs
        local region_info=$(grep "region size" "$log_file" | tail -1)
        if [[ "$region_info" =~ region\ size\ ([0-9]+)K ]]; then
            region_size="${BASH_REMATCH[1]}K"
        fi
        
        # Extract metaspace information
        local metaspace_info=$(grep "Metaspace.*used" "$log_file" | tail -1)
        local metaspace_used="N/A"
        local metaspace_committed="N/A"
        
        if [[ "$metaspace_info" =~ Metaspace.*used\ ([0-9]+)K,\ committed\ ([0-9]+)K ]]; then
            metaspace_used="${BASH_REMATCH[1]}K"
            metaspace_committed="${BASH_REMATCH[2]}K"
        fi
        
        # Count GC events (fix line break issue)
        local gc_count=$(grep -c "GC(" "$log_file" 2>/dev/null | tr -d '\n' || echo "0")
        
        # Store results in arrays for table generation
        GC_NAMES+=("$gc_name")
        EXECUTION_TIMES+=("$execution_time")
        HEAP_USED+=("$heap_used")
        HEAP_COMMITTED+=("$heap_committed")
        HEAP_RESERVED+=("$heap_reserved")
        GC_COUNTS+=("$gc_count")
        METASPACE_USED+=("$metaspace_used")
        METASPACE_COMMITTED+=("$metaspace_committed")
        REGION_SIZES+=("$region_size")
        STATUS_RESULTS+=("SUCCESS")
        
        # Write individual results to summary file
        echo "$gc_name Results:" >> "$SUMMARY_FILE"
        echo "  Execution Time: $execution_time" >> "$SUMMARY_FILE"
        echo "  Heap Used: $heap_used" >> "$SUMMARY_FILE"
        echo "  Heap Committed: $heap_committed" >> "$SUMMARY_FILE"
        echo "  Heap Reserved: $heap_reserved" >> "$SUMMARY_FILE"
        echo "  Region Size: $region_size" >> "$SUMMARY_FILE"
        echo "  GC Count: $gc_count" >> "$SUMMARY_FILE"
        echo "  Metaspace Used: $metaspace_used" >> "$SUMMARY_FILE"
        echo "  Metaspace Committed: $metaspace_committed" >> "$SUMMARY_FILE"
        echo >> "$SUMMARY_FILE"
    else
        # Store failed results
        GC_NAMES+=("$gc_name")
        EXECUTION_TIMES+=("N/A")
        HEAP_USED+=("N/A")
        HEAP_COMMITTED+=("N/A")
        HEAP_RESERVED+=("N/A")
        GC_COUNTS+=("N/A")
        METASPACE_USED+=("N/A")
        METASPACE_COMMITTED+=("N/A")
        REGION_SIZES+=("N/A")
        STATUS_RESULTS+=("FAILED")
    fi
}

# Function to generate markdown comparison table
generate_markdown_table() {
    local markdown_file="$LOG_DIR/gc_comparison_$TIMESTAMP.md"
    
    # Main comparison table only
    echo "| GC Type | Status | Execution Time (s) | Heap Used | Heap Committed | Heap Reserved | GC Events | Metaspace Used | Metaspace Committed | Region Size |" > "$markdown_file"
    echo "|---------|--------|-------------------|-----------|----------------|---------------|-----------|----------------|---------------------|-------------|" >> "$markdown_file"
    
    # Add data rows
    for i in "${!GC_NAMES[@]}"; do
        echo "| ${GC_NAMES[i]} | ${STATUS_RESULTS[i]} | ${EXECUTION_TIMES[i]} | ${HEAP_USED[i]} | ${HEAP_COMMITTED[i]} | ${HEAP_RESERVED[i]} | ${GC_COUNTS[i]} | ${METASPACE_USED[i]} | ${METASPACE_COMMITTED[i]} | ${REGION_SIZES[i]} |" >> "$markdown_file"
    done
    
    echo "[INFO] Markdown comparison table generated: $markdown_file"
    return 0
}

# Enhanced function to run with specific GC and comprehensive memory monitoring
run_with_gc() {
    local gc_name="$1"
    local gc_flags="$2"
    local safe_name=$(echo $gc_name | tr ' ' '-' | tr '[:upper:]' '[:lower:]')
    local gc_log="$LOG_DIR/gc-$safe_name-$TIMESTAMP.log"
    local memory_log="$LOG_DIR/memory-$safe_name-$TIMESTAMP.log"
    local output_log="$LOG_DIR/output-$safe_name-$TIMESTAMP.log"
    
    echo
    echo "=================== Running with $gc_name ==================="
    echo "GC Flags: $gc_flags"
    echo "GC Log: $gc_log"
    echo "Memory Log: $memory_log"
    echo "Output Log: $output_log"
    echo "-----------------------------------------------------------"
    
    # Record start time
    local start_time=$(date +%s.%N)
    
    # Run with comprehensive memory monitoring
    "$JAVA_HOME/bin/java" \
        --enable-preview \
        --add-modules=jdk.incubator.vector \
        $gc_flags \
        -Xlog:gc*:"$gc_log":time,tags \
        -Xlog:safepoint:"$gc_log":time,tags \
        -Xmx512m \
        -Xms128m \
        -cp "$CP" \
        "$MAIN_CLASS" > "$output_log" 2>&1
    
    local exit_code=$?
    local end_time=$(date +%s.%N)
    local execution_time=$(echo "$end_time - $start_time" | bc -l 2>/dev/null || echo "N/A")
    
    if [ $exit_code -ne 0 ]; then
        echo "[ERROR] $gc_name failed with exit code $exit_code"
        echo "Check $output_log for details"
        echo "$gc_name: FAILED (exit code $exit_code)" >> "$SUMMARY_FILE"
        
        # Store failed results in arrays
        GC_NAMES+=("$gc_name")
        EXECUTION_TIMES+=("N/A")
        HEAP_USED+=("N/A")
        HEAP_COMMITTED+=("N/A")
        HEAP_RESERVED+=("N/A")
        GC_COUNTS+=("N/A")
        METASPACE_USED+=("N/A")
        METASPACE_COMMITTED+=("N/A")
        REGION_SIZES+=("N/A")
        STATUS_RESULTS+=("FAILED")
    else
        echo "[SUCCESS] $gc_name completed successfully in ${execution_time}s"
        echo "Logs saved to:"
        echo "  - GC Log: $gc_log"
        echo "  - Output: $output_log"
        
        # Extract and record memory statistics
        extract_memory_stats "$gc_log" "$gc_name" "$execution_time"
        echo "$gc_name: SUCCESS (${execution_time}s)" >> "$SUMMARY_FILE"
        
        # Generate memory usage report for this GC
        if [ -f "$gc_log" ]; then
            echo "Memory Analysis for $gc_name:" > "$memory_log"
            echo "Execution Time: ${execution_time}s" >> "$memory_log"
            echo "=================================" >> "$memory_log"
            echo >> "$memory_log"
            
            # Heap usage over time
            echo "Heap Usage Timeline:" >> "$memory_log"
            grep -o "\[.*\].*Heap.*" "$gc_log" | head -20 >> "$memory_log" 2>/dev/null || echo "No heap data found" >> "$memory_log"
            echo >> "$memory_log"
            
            # GC events summary
            echo "GC Events Summary:" >> "$memory_log"
            grep "GC(" "$gc_log" | head -10 >> "$memory_log" 2>/dev/null || echo "No GC events found" >> "$memory_log"
        fi
    fi
    echo "=================== End $gc_name ==================="
    return $exit_code
}

# Test different garbage collectors
echo
echo "Starting GC comparison tests..."

# 1. G1 Garbage Collector (default in JDK 25)
run_with_gc "G1 GC" "-XX:+UseG1GC"

# 2. ZGC (Z Garbage Collector)
run_with_gc "ZGC" "-XX:+UnlockExperimentalVMOptions -XX:+UseZGC"

# 3. ZGC Generational (if available) - try different flag combinations
echo "[INFO] Checking ZGC Generational availability..."
if $JAVA_HOME/bin/java -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -XX:+UseGenerationalZGC -version >/dev/null 2>&1; then
    run_with_gc "ZGC Generational" "-XX:+UnlockExperimentalVMOptions -XX:+UseZGC -XX:+UseGenerationalZGC"
elif $JAVA_HOME/bin/java -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -XX:+UseGenZGC -version >/dev/null 2>&1; then
    run_with_gc "ZGC Generational" "-XX:+UnlockExperimentalVMOptions -XX:+UseZGC -XX:+UseGenZGC"
else
    echo
    echo "=================== ZGC Generational Not Available ==================="
    echo "ZGC Generational is not available in this JDK build"
    echo "Tried flags: -XX:+UseGenerationalZGC, -XX:+UseGenZGC"
    echo "===================================================================="
fi

# 4. Shenandoah GC - check availability
echo "[INFO] Checking Shenandoah GC availability..."
if $JAVA_HOME/bin/java -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -version >/dev/null 2>&1; then
    run_with_gc "Shenandoah GC" "-XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC"
elif $JAVA_HOME/bin/java -XX:+UseShenandoahGC -version >/dev/null 2>&1; then
    run_with_gc "Shenandoah GC" "-XX:+UseShenandoahGC"
else
    echo
    echo "=================== Shenandoah GC Not Available ==================="
    echo "Shenandoah GC is not available in this JDK build"
    echo "This may require a specific JDK build with Shenandoah support"
    echo "================================================================="
fi

# 5. Shenandoah Generational GC (if available) - try different flag combinations
echo "[INFO] Checking Shenandoah Generational availability..."
if $JAVA_HOME/bin/java -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseGenerationalShenandoahGC -version >/dev/null 2>&1; then
    run_with_gc "Shenandoah Generational" "-XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseGenerationalShenandoahGC"
elif $JAVA_HOME/bin/java -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseShenandoahGen -version >/dev/null 2>&1; then
    run_with_gc "Shenandoah Generational" "-XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseShenandoahGen"
else
    echo
    echo "=================== Shenandoah Generational Not Available ==================="
    echo "Shenandoah Generational GC is not available in this JDK build"
    echo "Tried flags: -XX:+UseGenerationalShenandoahGC, -XX:+UseShenandoahGen"
    echo "This feature may require specific JDK 25+ builds"
    echo "==========================================================================="
fi

# Generate final comparison report
echo
echo "[INFO] Generating memory consumption comparison report..."

# Generate markdown table
generate_markdown_table

# Create detailed comparison
echo >> "$SUMMARY_FILE"
echo "Detailed Analysis:" >> "$SUMMARY_FILE"
echo "==================" >> "$SUMMARY_FILE"

# List all generated log files
echo "Generated Log Files:" >> "$SUMMARY_FILE"
ls -la "$LOG_DIR"/*$TIMESTAMP* 2>/dev/null | while read line; do
    echo "  $line" >> "$SUMMARY_FILE"
done 2>/dev/null || echo "  No log files found" >> "$SUMMARY_FILE"

echo >> "$SUMMARY_FILE"
echo "Recommendations:" >> "$SUMMARY_FILE"
echo "1. Review individual GC logs for detailed memory patterns" >> "$SUMMARY_FILE"
echo "2. Compare execution times and GC overhead" >> "$SUMMARY_FILE"
echo "3. Analyze heap usage patterns in memory logs" >> "$SUMMARY_FILE"
echo "4. Consider application-specific memory requirements" >> "$SUMMARY_FILE"

echo
echo "=================== All GC Memory Tests Completed ==================="
echo "Results Summary: $SUMMARY_FILE"
echo "Markdown Table: $LOG_DIR/gc_comparison_$TIMESTAMP.md"
echo "Log Directory: $LOG_DIR"
echo "Memory Analysis Files: $LOG_DIR/memory-*-$TIMESTAMP.log"
echo
echo "Quick Summary:"
echo "=============="
cat "$SUMMARY_FILE" | grep -E "SUCCESS|FAILED" || echo "No results found"
echo
echo "📊 View the detailed comparison table in Markdown format:"
echo "   $LOG_DIR/gc_comparison_$TIMESTAMP.md"
echo
echo "Note: Some GC options may not be available in all JDK builds."
echo "If you see errors, it might be due to:"
echo "1. GC not available in your JDK build"
echo "2. GC requires additional compilation flags"
echo "3. Generational variants may be experimental"
echo "================================================================"