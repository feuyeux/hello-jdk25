#!/bin/bash

# JMH-based GC Algorithm Performance Benchmark Execution Script
# Runs comprehensive benchmarks comparing 5 garbage collectors
# Results are automatically analyzed and formatted into comparison reports

set -e  # Exit on any error

# Configuration
export JAVA_HOME=${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home}
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS="--add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/log"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${BLUE}===============================================================================${NC}"
echo -e "${BLUE}                    JMH GC ALGORITHM PERFORMANCE BENCHMARK${NC}"
echo -e "${BLUE}===============================================================================${NC}"
echo -e "${CYAN}Timestamp:${NC} $TIMESTAMP"
echo -e "${CYAN}JAVA_HOME:${NC} $JAVA_HOME"
echo -e "${CYAN}Script Directory:${NC} $SCRIPT_DIR"
echo -e "${CYAN}Log Directory:${NC} $LOG_DIR"
echo ""

# Function to print section headers
print_section() {
    echo -e "${PURPLE}$1${NC}"
    echo -e "${PURPLE}$(printf '=%.0s' $(seq 1 ${#1}))${NC}"
}

# Function to print step info
print_step() {
    echo -e "${YELLOW}[INFO]${NC} $1"
}

# Function to print success message
print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# Function to print error message
print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check prerequisites
check_prerequisites() {
    print_section "CHECKING PREREQUISITES"
    
    # Check Java installation
    if [ ! -f "$JAVA_HOME/bin/java" ]; then
        print_error "Java not found at $JAVA_HOME/bin/java"
        print_error "Please set JAVA_HOME to point to your JDK 25 installation"
        exit 1
    fi
    
    # Check Java version
    JAVA_VERSION=$($JAVA_HOME/bin/java --version | head -1)
    print_step "Java Version: $JAVA_VERSION"
    
    # Check if it's JDK 25 or later
    if ! echo "$JAVA_VERSION" | grep -E "(java 25|java 2[6-9]|java [3-9][0-9])" > /dev/null; then
        print_error "JDK 25 or later is required for this benchmark"
        print_error "Current version: $JAVA_VERSION"
        exit 1
    fi
    
    # Check Maven
    if ! command -v mvn &> /dev/null; then
        print_error "Maven is required but not found in PATH"
        exit 1
    fi
    
    print_step "Maven Version: $(mvn --version | head -1)"
    
    # Check available memory
    AVAILABLE_MEMORY=$($JAVA_HOME/bin/java -XX:+PrintFlagsFinal -version 2>&1 | grep MaxHeapSize | awk '{print $4}')
    if [ -n "$AVAILABLE_MEMORY" ] && [ "$AVAILABLE_MEMORY" -gt 0 ] 2>/dev/null; then
        AVAILABLE_MEMORY_MB=$((AVAILABLE_MEMORY / 1024 / 1024))
        print_step "Available Heap Memory: ${AVAILABLE_MEMORY_MB}MB"
        
        if [ "$AVAILABLE_MEMORY_MB" -lt 1024 ]; then
            print_error "At least 1GB of heap memory is recommended for benchmarks"
            print_error "Current available: ${AVAILABLE_MEMORY_MB}MB"
            echo -e "${YELLOW}Consider increasing JVM memory or system RAM${NC}"
        fi
    else
        print_step "Available Heap Memory: Could not determine (using system default)"
    fi
    
    print_success "All prerequisites met"
    echo ""
}

# Function to setup environment
setup_environment() {
    print_section "SETTING UP ENVIRONMENT"
    
    # Create log directory
    print_step "Creating log directory: $LOG_DIR"
    mkdir -p "$LOG_DIR"
    
    # Clean up old benchmark results (optional)
    if [ "$1" == "--clean" ]; then
        print_step "Cleaning up old benchmark results"
        find "$LOG_DIR" -name "jmh_results_*" -type f -mtime +7 -delete 2>/dev/null || true
        find "$LOG_DIR" -name "gc_benchmark_*" -type f -mtime +7 -delete 2>/dev/null || true
    fi
    
    print_success "Environment setup complete"
    echo ""
}

# Function to compile the project
compile_project() {
    print_section "BUILDING PROJECT"
    
    print_step "Running Maven clean package..."
    if mvn clean package -DskipTests -q; then
        print_success "Project built successfully"
    else
        print_error "Build failed"
        exit 1
    fi
    
    # Check if the shaded jar exists
    if [ ! -f "target/benchmarks.jar" ]; then
        print_error "Benchmarks JAR not found at target/benchmarks.jar"
        exit 1
    fi
    
    print_success "Benchmarks JAR ready at target/benchmarks.jar"
    echo ""
}

# Function to check GC availability
check_gc_availability() {
    print_section "CHECKING GC AVAILABILITY"
    
    local gc_configs=(
        "G1 GC:-XX:+UseG1GC"
        "ZGC:-XX:+UnlockExperimentalVMOptions -XX:+UseZGC"
        "ZGC Generational:-XX:+UnlockExperimentalVMOptions -XX:+UseZGC -XX:+UseGenerationalZGC"
        "Shenandoah GC:-XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC"
        "Shenandoah Generational:-XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseGenerationalShenandoahGC"
    )
    
    echo -e "${CYAN}Testing GC availability...${NC}"
    echo ""
    
    for config in "${gc_configs[@]}"; do
        local gc_name="${config%%:*}"
        local gc_flags="${config#*:}"
        
        if $JAVA_HOME/bin/java $gc_flags -version >/dev/null 2>&1; then
            print_success "$gc_name is available"
        else
            print_error "$gc_name is NOT available"
        fi
    done
    echo ""
}

# Function to run benchmarks
run_benchmarks() {
    print_section "RUNNING BENCHMARKS"
    
    print_step "Starting JMH benchmark execution..."
    print_step "This may take 30-60 minutes depending on your system"
    echo ""
    
    # Create a log file for the execution
    local execution_log="$LOG_DIR/benchmark_execution_$TIMESTAMP.log"
    print_step "Execution log: $execution_log"
    echo ""
    
    # Run the benchmark runner using the shaded JAR
    if $JAVA_HOME/bin/java --enable-preview --add-modules=jdk.incubator.vector \
        -cp target/benchmarks.jar org.feuyeux.jdk25.benchmark.GCBenchmarkRunner 2>&1 | tee "$execution_log"; then
        print_success "Benchmarks completed successfully"
    else
        print_error "Benchmark execution failed"
        print_error "Check the execution log: $execution_log"
        exit 1
    fi
    echo ""
}

# Function to generate summary
generate_summary() {
    print_section "GENERATING SUMMARY"
    
    # Find the latest results
    local latest_markdown=$(find "$LOG_DIR" -name "gc_benchmark_comparison_*.md" -type f -exec ls -t {} + | head -1)
    local latest_csv=$(find "$LOG_DIR" -name "gc_benchmark_results_*.csv" -type f -exec ls -t {} + | head -1)
    local latest_detailed=$(find "$LOG_DIR" -name "gc_benchmark_detailed_*.txt" -type f -exec ls -t {} + | head -1)
    
    echo -e "${CYAN}Generated Reports:${NC}"
    [ -n "$latest_markdown" ] && echo -e "  📊 Markdown Report: ${latest_markdown}"
    [ -n "$latest_csv" ] && echo -e "  📈 CSV Data: ${latest_csv}"
    [ -n "$latest_detailed" ] && echo -e "  📝 Detailed Report: ${latest_detailed}"
    echo ""
    
    # Show quick summary if markdown report exists
    if [ -n "$latest_markdown" ] && [ -f "$latest_markdown" ]; then
        echo -e "${CYAN}Quick Summary (from markdown report):${NC}"
        echo -e "${BLUE}$(head -20 "$latest_markdown" | grep -E "^#|^\|" | head -15)${NC}"
        echo ""
    fi
    
    print_success "Summary generation complete"
}

# Function to cleanup and finalize
finalize() {
    print_section "FINALIZING"
    
    # Count generated files
    local result_files=$(find "$LOG_DIR" -name "*$TIMESTAMP*" -type f | wc -l)
    print_step "Generated $result_files result files"
    
    # Calculate disk usage
    local disk_usage=$(du -sh "$LOG_DIR" 2>/dev/null | cut -f1)
    print_step "Total log directory size: $disk_usage"
    
    echo ""
    echo -e "${GREEN}🎉 BENCHMARK SUITE COMPLETED SUCCESSFULLY! 🎉${NC}"
    echo ""
    echo -e "${CYAN}Next Steps:${NC}"
    echo -e "  1. Review the markdown report for detailed analysis"
    echo -e "  2. Import the CSV data into your preferred analysis tool"
    echo -e "  3. Compare results with previous benchmark runs"
    echo -e "  4. Use findings to optimize your application's GC configuration"
    echo ""
    echo -e "${BLUE}===============================================================================${NC}"
    echo -e "${BLUE}                        BENCHMARK EXECUTION COMPLETE${NC}"
    echo -e "${BLUE}===============================================================================${NC}"
}

# Function to show help
show_help() {
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  --help          Show this help message"
    echo "  --clean         Clean up old benchmark results before running"
    echo "  --check-only    Only check prerequisites and GC availability"
    echo "  --compile-only  Only compile the project"
    echo ""
    echo "Examples:"
    echo "  $0                    # Run full benchmark suite"
    echo "  $0 --clean           # Clean old results and run benchmarks"
    echo "  $0 --check-only      # Check system prerequisites only"
    echo ""
}

# Main execution flow
main() {
    case "${1:-}" in
        --help|-h)
            show_help
            exit 0
            ;;
        --check-only)
            check_prerequisites
            check_gc_availability
            exit 0
            ;;
        --compile-only)
            check_prerequisites
            setup_environment
            compile_project
            exit 0
            ;;
        --clean)
            check_prerequisites
            setup_environment --clean
            compile_project
            check_gc_availability
            run_benchmarks
            generate_summary
            finalize
            ;;
        "")
            check_prerequisites
            setup_environment
            compile_project
            check_gc_availability
            run_benchmarks
            generate_summary
            finalize
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            show_help
            exit 1
            ;;
    esac
}

# Handle interruption gracefully
trap 'echo -e "\n${RED}Benchmark execution interrupted by user${NC}"; exit 1' INT TERM

# Execute main function with all arguments
main "$@"