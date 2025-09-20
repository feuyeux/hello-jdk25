package org.feuyeux.jdk25.benchmark;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collects and analyzes benchmark results from different GC configurations.
 * Generates comprehensive comparison reports with memory and performance metrics.
 */
public class ResultsCollector {
    
    private final String logDirectory;
    private final String resultDirectory;
    private final String timestamp;
    private final Map<String, GCResults> gcResults;
    
    private static final Pattern BENCHMARK_RESULT_PATTERN = 
        Pattern.compile("(\\w+\\.\\w+)\\s+avgt\\s+\\d+\\s+([\\d.]+)\\s+\\±\\s+([\\d.]+)\\s+(\\w+)");
    
    private static final Pattern MEMORY_DELTA_PATTERN = 
        Pattern.compile("(\\w+.*) - Memory delta: ([\\d.]+ \\w+)");
    
    public ResultsCollector() {
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        this.logDirectory = "log";
        this.resultDirectory = "result";
        this.gcResults = new ConcurrentHashMap<>();
        
        // Ensure log and result directories exist
        try {
            Files.createDirectories(Paths.get(logDirectory));
            Files.createDirectories(Paths.get(resultDirectory));
        } catch (IOException e) {
            System.err.println("Failed to create directories: " + e.getMessage());
        }
    }
    
    /**
     * Data structure to hold results for a specific GC configuration
     */
    public static class GCResults {
        public final String gcName;
        public final String gcFlags;
        public final Map<String, BenchmarkResult> benchmarkResults;
        public final Map<String, String> memoryDeltas;
        public final long totalExecutionTime;
        public final boolean successful;
        public String errorMessage;
        
        public GCResults(String gcName, String gcFlags) {
            this.gcName = gcName;
            this.gcFlags = gcFlags;
            this.benchmarkResults = new HashMap<>();
            this.memoryDeltas = new HashMap<>();
            this.totalExecutionTime = 0;
            this.successful = true;
        }
        
        public GCResults(String gcName, String gcFlags, String errorMessage) {
            this.gcName = gcName;
            this.gcFlags = gcFlags;
            this.benchmarkResults = new HashMap<>();
            this.memoryDeltas = new HashMap<>();
            this.totalExecutionTime = 0;
            this.successful = false;
            this.errorMessage = errorMessage;
        }
    }
    
    /**
     * Individual benchmark result
     */
    public static class BenchmarkResult {
        public final String benchmarkName;
        public final double averageTime;
        public final double error;
        public final String unit;
        public final double throughput; // ops/sec
        
        public BenchmarkResult(String benchmarkName, double averageTime, double error, String unit) {
            this.benchmarkName = benchmarkName;
            this.averageTime = averageTime;
            this.error = error;
            this.unit = unit;
            
            // Calculate approximate throughput
            if ("ms".equals(unit)) {
                this.throughput = 1000.0 / averageTime;
            } else if ("us".equals(unit)) {
                this.throughput = 1_000_000.0 / averageTime;
            } else if ("ns".equals(unit)) {
                this.throughput = 1_000_000_000.0 / averageTime;
            } else {
                this.throughput = 0.0;
            }
        }
    }
    
    /**
     * Parse JMH output and extract benchmark results
     */
    public void parseJMHResults(String gcName, Path outputFile) {
        try {
            List<String> lines = Files.readAllLines(outputFile);
            GCResults results = gcResults.computeIfAbsent(gcName, 
                name -> new GCResults(name, getGCFlags(name)));
            
            for (String line : lines) {
                // Parse benchmark results
                Matcher benchmarkMatcher = BENCHMARK_RESULT_PATTERN.matcher(line);
                if (benchmarkMatcher.find()) {
                    String benchmarkName = benchmarkMatcher.group(1);
                    double averageTime = Double.parseDouble(benchmarkMatcher.group(2));
                    double error = Double.parseDouble(benchmarkMatcher.group(3));
                    String unit = benchmarkMatcher.group(4);
                    
                    results.benchmarkResults.put(benchmarkName, 
                        new BenchmarkResult(benchmarkName, averageTime, error, unit));
                }
                
                // Parse memory deltas
                Matcher memoryMatcher = MEMORY_DELTA_PATTERN.matcher(line);
                if (memoryMatcher.find()) {
                    String operation = memoryMatcher.group(1);
                    String memoryDelta = memoryMatcher.group(2);
                    results.memoryDeltas.put(operation, memoryDelta);
                }
            }
            
        } catch (IOException e) {
            System.err.println("Failed to parse results for " + gcName + ": " + e.getMessage());
            gcResults.put(gcName, new GCResults(gcName, getGCFlags(gcName), e.getMessage()));
        }
    }
    
    /**
     * Record failed GC execution
     */
    public void recordFailure(String gcName, String errorMessage) {
        gcResults.put(gcName, new GCResults(gcName, getGCFlags(gcName), errorMessage));
    }
    
    /**
     * Generate comprehensive comparison report
     */
    public void generateComparisonReport() {
        try {
            generateMarkdownReport();
            generateDetailedTextReport();
            generateCSVReport();
            System.out.println("\\n=== Results Collection Complete ===");
            System.out.println("Reports generated in:");
            System.out.println("- Log directory: " + logDirectory);
            System.out.println("- Result directory: " + resultDirectory);
            System.out.println("- Markdown: gc_benchmark_comparison_" + timestamp + ".md");
            System.out.println("- Detailed: gc_benchmark_detailed_" + timestamp + ".txt");
            System.out.println("- CSV: gc_benchmark_results_" + timestamp + ".csv");
        } catch (IOException e) {
            System.err.println("Failed to generate reports: " + e.getMessage());
        }
    }
    
    /**
     * Generate markdown comparison report
     */
    private void generateMarkdownReport() throws IOException {
        Path reportPath = Paths.get(resultDirectory, "gc_benchmark_comparison_" + timestamp + ".md");
        
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(reportPath))) {
            // Summary table only - concise format
            generateSummaryTable(writer);
            
            // Performance comparison table
            generatePerformanceTable(writer);
        }
    }
    
    /**
     * Generate summary table in markdown
     */
    private void generateSummaryTable(PrintWriter writer) {
        writer.println("| GC Algorithm | Status | Avg Time (ms) | Throughput (ops/sec) | Memory Efficiency |");
        writer.println("|--------------|--------|---------------|---------------------|-------------------|");
        
        for (GCResults results : gcResults.values()) {
            if (results.successful) {
                double avgTime = results.benchmarkResults.values().stream()
                    .mapToDouble(br -> br.averageTime)
                    .average()
                    .orElse(0.0);
                
                double avgThroughput = results.benchmarkResults.values().stream()
                    .mapToDouble(br -> br.throughput)
                    .average()
                    .orElse(0.0);
                
                writer.printf("| %s | ✅ | %.2f | %.0f | Good |%n",
                    results.gcName, avgTime, avgThroughput);
            } else {
                writer.printf("| %s | ❌ | N/A | N/A | N/A |%n", results.gcName);
            }
        }
        writer.println();
    }
    
    /**
     * Generate performance comparison table
     */
    private void generatePerformanceTable(PrintWriter writer) {
        // Get all unique benchmark names
        Set<String> allBenchmarks = gcResults.values().stream()
            .filter(r -> r.successful)
            .flatMap(r -> r.benchmarkResults.keySet().stream())
            .collect(HashSet::new, HashSet::add, HashSet::addAll);
        
        if (allBenchmarks.isEmpty()) {
            return;
        }
        
        writer.println();
        writer.println("| Benchmark | G1 GC | ZGC | Winner |");
        writer.println("|-----------|-------|-----|--------|");
        
        for (String benchmark : allBenchmarks) {
            String g1Result = "N/A";
            String zgcResult = "N/A";
            String winner = "N/A";
            
            double g1Time = Double.MAX_VALUE;
            double zgcTime = Double.MAX_VALUE;
            
            for (GCResults results : gcResults.values()) {
                if (results.successful && results.benchmarkResults.containsKey(benchmark)) {
                    double time = results.benchmarkResults.get(benchmark).averageTime;
                    String timeStr = String.format("%.2f ms", time);
                    
                    if (results.gcName.equals("G1 GC")) {
                        g1Result = timeStr;
                        g1Time = time;
                    } else if (results.gcName.equals("ZGC")) {
                        zgcResult = timeStr;
                        zgcTime = time;
                    }
                }
            }
            
            if (g1Time != Double.MAX_VALUE && zgcTime != Double.MAX_VALUE) {
                winner = g1Time < zgcTime ? "G1 GC" : "ZGC";
            } else if (g1Time != Double.MAX_VALUE) {
                winner = "G1 GC";
            } else if (zgcTime != Double.MAX_VALUE) {
                winner = "ZGC";
            }
            
            String shortBenchmark = benchmark.substring(benchmark.lastIndexOf('.') + 1);
            writer.printf("| %s | %s | %s | %s |%n", shortBenchmark, g1Result, zgcResult, winner);
        }
        writer.println();
    }
    
    /**
     * Generate detailed section for each GC
     */
    private void generateDetailedGCSection(PrintWriter writer, GCResults results) {
        writer.println("### " + results.gcName);
        writer.println();
        writer.println("**Configuration:** `" + results.gcFlags + "`");
        writer.println();
        
        if (!results.successful) {
            writer.println("**Status:** ❌ FAILED");
            if (results.errorMessage != null) {
                writer.println("**Error:** " + results.errorMessage);
            }
            writer.println();
            return;
        }
        
        writer.println("**Status:** ✅ SUCCESS");
        writer.println();
        
        // Benchmark results table
        if (!results.benchmarkResults.isEmpty()) {
            writer.println("#### Performance Results");
            writer.println();
            writer.println("| Benchmark | Avg Time | Error | Unit | Throughput (ops/sec) |");
            writer.println("|-----------|----------|-------|------|---------------------|");
            
            results.benchmarkResults.values().forEach(br -> {
                writer.printf("| %s | %.3f | ±%.3f | %s | %.0f |%n",
                    br.benchmarkName, br.averageTime, br.error, br.unit, br.throughput);
            });
            writer.println();
        }
        
        // Memory usage
        if (!results.memoryDeltas.isEmpty()) {
            writer.println("#### Memory Usage");
            writer.println();
            results.memoryDeltas.forEach((operation, delta) -> {
                writer.println("- **" + operation + ":** " + delta);
            });
            writer.println();
        }
    }
    
    /**
     * Generate performance comparison section
     */
    private void generatePerformanceComparison(PrintWriter writer) {
        writer.println("## Performance Comparison");
        writer.println();
        
        // Find best and worst performers for each benchmark
        Map<String, String> bestPerformers = new HashMap<>();
        Map<String, String> worstPerformers = new HashMap<>();
        
        Set<String> allBenchmarks = gcResults.values().stream()
            .filter(r -> r.successful)
            .flatMap(r -> r.benchmarkResults.keySet().stream())
            .collect(HashSet::new, HashSet::add, HashSet::addAll);
        
        for (String benchmark : allBenchmarks) {
            double bestTime = Double.MAX_VALUE;
            double worstTime = 0;
            String bestGC = "";
            String worstGC = "";
            
            for (GCResults results : gcResults.values()) {
                if (results.successful && results.benchmarkResults.containsKey(benchmark)) {
                    double time = results.benchmarkResults.get(benchmark).averageTime;
                    if (time < bestTime) {
                        bestTime = time;
                        bestGC = results.gcName;
                    }
                    if (time > worstTime) {
                        worstTime = time;
                        worstGC = results.gcName;
                    }
                }
            }
            
            bestPerformers.put(benchmark, bestGC + " (" + String.format("%.3f", bestTime) + " ms)");
            worstPerformers.put(benchmark, worstGC + " (" + String.format("%.3f", worstTime) + " ms)");
        }
        
        writer.println("### Best Performers by Benchmark");
        bestPerformers.forEach((benchmark, gc) -> {
            writer.println("- **" + benchmark + ":** " + gc);
        });
        writer.println();
        
        writer.println("### Slowest Performers by Benchmark");
        worstPerformers.forEach((benchmark, gc) -> {
            writer.println("- **" + benchmark + ":** " + gc);
        });
        writer.println();
    }
    
    /**
     * Generate memory analysis section
     */
    private void generateMemoryAnalysis(PrintWriter writer) {
        writer.println("## Memory Usage Analysis");
        writer.println();
        
        writer.println("Memory consumption patterns observed during benchmarks:");
        writer.println();
        
        for (GCResults results : gcResults.values()) {
            if (results.successful && !results.memoryDeltas.isEmpty()) {
                writer.println("### " + results.gcName + " Memory Patterns");
                results.memoryDeltas.forEach((operation, delta) -> {
                    writer.println("- " + operation + ": " + delta);
                });
                writer.println();
            }
        }
    }
    
    /**
     * Generate recommendations section
     */
    private void generateRecommendations(PrintWriter writer) {
        writer.println("## Recommendations");
        writer.println();
        
        List<GCResults> successfulResults = gcResults.values().stream()
            .filter(r -> r.successful)
            .sorted(Comparator.comparing((GCResults r) -> 
                r.benchmarkResults.values().stream()
                    .mapToDouble(br -> br.averageTime)
                    .average()
                    .orElse(Double.MAX_VALUE)))
            .toList();
        
        if (!successfulResults.isEmpty()) {
            writer.println("### Top Performers (by average response time)");
            for (int i = 0; i < Math.min(3, successfulResults.size()); i++) {
                GCResults results = successfulResults.get(i);
                double avgTime = results.benchmarkResults.values().stream()
                    .mapToDouble(br -> br.averageTime)
                    .average()
                    .orElse(0.0);
                
                writer.printf("%d. **%s** - %.2f ms average%n", i + 1, results.gcName, avgTime);
                writer.println("   - " + getRecommendation(results.gcName));
                writer.println();
            }
        }
        
        writer.println("### General Guidelines");
        writer.println("- **G1 GC**: Good default choice for most workloads");
        writer.println("- **ZGC**: Best for low-latency requirements and large heaps");
        writer.println("- **Shenandoah**: Good alternative for low-pause applications");
        writer.println("- **Generational variants**: May offer better performance for workloads with clear generational patterns");
        writer.println();
    }
    
    /**
     * Get recommendation for specific GC
     */
    private String getRecommendation(String gcName) {
        return switch (gcName.toLowerCase()) {
            case "g1 gc" -> "General purpose, good default choice";
            case "zgc" -> "Ultra-low latency, large heap applications";
            case "zgc generational" -> "ZGC with generational optimization";
            case "shenandoah gc" -> "Low-pause alternative to G1";
            case "shenandoah generational" -> "Shenandoah with generational benefits";
            default -> "Specialized use cases";
        };
    }
    
    /**
     * Generate detailed text report
     */
    private void generateDetailedTextReport() throws IOException {
        Path reportPath = Paths.get(logDirectory, "gc_benchmark_detailed_" + timestamp + ".txt");
        
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(reportPath))) {
            writer.println("=".repeat(80));
            writer.println("GC ALGORITHM PERFORMANCE BENCHMARK - DETAILED RESULTS");
            writer.println("=".repeat(80));
            writer.println("Generated: " + LocalDateTime.now());
            writer.println("JDK Version: " + System.getProperty("java.version"));
            writer.println();
            
            for (GCResults results : gcResults.values()) {
                writer.println("-".repeat(50));
                writer.println("GC: " + results.gcName);
                writer.println("Flags: " + results.gcFlags);
                writer.println("Status: " + (results.successful ? "SUCCESS" : "FAILED"));
                
                if (!results.successful) {
                    writer.println("Error: " + results.errorMessage);
                } else {
                    writer.println("\\nBenchmark Results:");
                    results.benchmarkResults.forEach((name, result) -> {
                        writer.printf("  %s: %.3f ± %.3f %s (%.0f ops/sec)%n",
                            name, result.averageTime, result.error, result.unit, result.throughput);
                    });
                    
                    writer.println("\\nMemory Deltas:");
                    results.memoryDeltas.forEach((operation, delta) -> {
                        writer.println("  " + operation + ": " + delta);
                    });
                }
                writer.println();
            }
        }
    }
    
    /**
     * Generate CSV report for data analysis
     */
    private void generateCSVReport() throws IOException {
        Path reportPath = Paths.get(logDirectory, "gc_benchmark_results_" + timestamp + ".csv");
        
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(reportPath))) {
            writer.println("GC_Name,GC_Flags,Status,Benchmark,Avg_Time_ms,Error_ms,Unit,Throughput_ops_sec");
            
            for (GCResults results : gcResults.values()) {
                if (results.successful) {
                    for (BenchmarkResult br : results.benchmarkResults.values()) {
                        writer.printf("%s,\\\"%s\\\",SUCCESS,%s,%.3f,%.3f,%s,%.0f%n",
                            results.gcName, results.gcFlags, br.benchmarkName,
                            br.averageTime, br.error, br.unit, br.throughput);
                    }
                } else {
                    writer.printf("%s,\\\"%s\\\",FAILED,N/A,N/A,N/A,N/A,N/A%n",
                        results.gcName, results.gcFlags);
                }
            }
        }
    }
    
    /**
     * Get GC flags for a given GC name
     */
    private String getGCFlags(String gcName) {
        return switch (gcName.toLowerCase()) {
            case "g1 gc" -> "-XX:+UseG1GC";
            case "zgc" -> "-XX:+UnlockExperimentalVMOptions -XX:+UseZGC";
            case "zgc generational" -> "-XX:+UnlockExperimentalVMOptions -XX:+UseZGC -XX:+UseGenerationalZGC";
            case "shenandoah gc" -> "-XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC";
            case "shenandoah generational" -> "-XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseGenerationalShenandoahGC";
            default -> "";
        };
    }
}