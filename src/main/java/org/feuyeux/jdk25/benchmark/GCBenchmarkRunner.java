package org.feuyeux.jdk25.benchmark;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * GC Benchmark Runner - Executes JMH benchmarks with different GC configurations
 * and collects comprehensive performance data for comparison
 */
public class GCBenchmarkRunner {
    
    private static final String LOG_DIR = "bench-log";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    /**
     * GC Configuration for testing
     */
    public static class GCConfig {
        private final String name;
        private final List<String> jvmArgs;
        private final String description;
        
        public GCConfig(String name, String description, String... jvmArgs) {
            this.name = name;
            this.description = description;
            this.jvmArgs = Arrays.asList(jvmArgs);
        }
        
        public String getName() { return name; }
        public List<String> getJvmArgs() { return jvmArgs; }
        public String getDescription() { return description; }
    }
    
    // Standard GC configurations for JDK 25 - simplified for direct comparison
    private static final List<GCConfig> GC_CONFIGURATIONS = Arrays.asList(
        new GCConfig("G1GC", "G1 Garbage Collector - Low latency collector",
            "-XX:+UseG1GC",
            "-XX:MaxGCPauseMillis=200"
        ),
        
        new GCConfig("ZGC", "ZGC - Ultra-low latency collector",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+UseZGC"
        )
    );
    
    /**
     * Benchmark configuration options
     */
    public static class BenchmarkOptions {
        private int warmupIterations = 3;
        private int measurementIterations = 5;
        private int warmupTime = 10;
        private int measurementTime = 15;
        private TimeUnit timeUnit = TimeUnit.SECONDS;
        private int forks = 1;
        private String heapSize = "2g";
        private boolean includeGCProfiler = true;
        private List<String> benchmarkPatterns = Arrays.asList(".*");
        
        // Builder pattern setters
        public BenchmarkOptions warmupIterations(int iterations) {
            this.warmupIterations = iterations;
            return this;
        }
        
        public BenchmarkOptions measurementIterations(int iterations) {
            this.measurementIterations = iterations;
            return this;
        }
        
        public BenchmarkOptions warmupTime(int time) {
            this.warmupTime = time;
            return this;
        }
        
        public BenchmarkOptions measurementTime(int time) {
            this.measurementTime = time;
            return this;
        }
        
        public BenchmarkOptions timeUnit(TimeUnit unit) {
            this.timeUnit = unit;
            return this;
        }
        
        public BenchmarkOptions forks(int forks) {
            this.forks = forks;
            return this;
        }
        
        public BenchmarkOptions heapSize(String size) {
            this.heapSize = size;
            return this;
        }
        
        public BenchmarkOptions includeGCProfiler(boolean include) {
            this.includeGCProfiler = include;
            return this;
        }
        
        public BenchmarkOptions benchmarkPatterns(String... patterns) {
            this.benchmarkPatterns = Arrays.asList(patterns);
            return this;
        }
        
        // Getters
        public int getWarmupIterations() { return warmupIterations; }
        public int getMeasurementIterations() { return measurementIterations; }
        public int getWarmupTime() { return warmupTime; }
        public int getMeasurementTime() { return measurementTime; }
        public TimeUnit getTimeUnit() { return timeUnit; }
        public int getForks() { return forks; }
        public String getHeapSize() { return heapSize; }
        public boolean isIncludeGCProfiler() { return includeGCProfiler; }
        public List<String> getBenchmarkPatterns() { return benchmarkPatterns; }
    }
    
    /**
     * Run complete GC comparison benchmark suite
     */
    public static void runCompleteBenchmarkSuite() throws Exception {
        System.out.println("=".repeat(80));
        System.out.println("                   GC PERFORMANCE BENCHMARK SUITE");
        System.out.println("=".repeat(80));
        System.out.println();
        
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        
        // Create log directory
        Path logDir = Paths.get(LOG_DIR);
        Files.createDirectories(logDir);
        
        // Run benchmarks for each GC configuration
        Map<String, Collection<RunResult>> allResults = new LinkedHashMap<>();
        
        // Run only G1GC and ZGC for direct comparison
        List<GCConfig> comparisonConfigs = Arrays.asList(
            GC_CONFIGURATIONS.get(0), // G1GC
            GC_CONFIGURATIONS.get(1)  // ZGC
        );
        
        for (GCConfig gcConfig : comparisonConfigs) {
            if (isGCAvailable(gcConfig)) {
                System.out.println("🚀 Running benchmarks with " + gcConfig.getName() + "...");
                System.out.println("   " + gcConfig.getDescription());
                System.out.println();
                
                try {
                    Collection<RunResult> results = runBenchmarkWithGC(gcConfig, new BenchmarkOptions());
                    allResults.put(gcConfig.getName(), results);
                    
                    System.out.println("✅ Completed " + gcConfig.getName() + " benchmarks");
                    System.out.println();
                    
                } catch (Exception e) {
                    System.err.println("❌ Failed to run benchmarks with " + gcConfig.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("⚠️ Skipping " + gcConfig.getName() + " - not available in this JVM");
            }
        }
        
        // Generate comparison reports
        if (!allResults.isEmpty()) {
            generateComparisonReports(allResults, timestamp);
            generateJSONReport(allResults, timestamp);
            generateSeparateJSONReports(allResults, timestamp);
        } else {
            System.err.println("❌ No benchmark results were collected!");
        }
    }
    
    /**
     * Run benchmark with specific GC configuration
     */
    public static Collection<RunResult> runBenchmarkWithGC(GCConfig gcConfig, BenchmarkOptions options) 
            throws RunnerException {
        
        // Build JVM arguments
        List<String> jvmArgs = new ArrayList<>();
        jvmArgs.add("-Xms" + options.getHeapSize());
        jvmArgs.add("-Xmx" + options.getHeapSize());
        jvmArgs.addAll(gcConfig.getJvmArgs());
        
        // Additional JVM tuning (OS-specific)
        if (System.getProperty("os.name").toLowerCase().contains("linux")) {
            jvmArgs.add("-XX:+UseTransparentHugePages");
        }
        jvmArgs.addAll(Arrays.asList(
            "-XX:+UseCompressedOops",
            "-XX:+UseCompressedClassPointers",
            "-server"
        ));
        
        OptionsBuilder optionsBuilder = new OptionsBuilder();
        optionsBuilder.warmupIterations(options.getWarmupIterations());
        optionsBuilder.measurementIterations(options.getMeasurementIterations());
        optionsBuilder.warmupTime(TimeValue.seconds(options.getWarmupTime()));
        optionsBuilder.measurementTime(TimeValue.seconds(options.getMeasurementTime()));
        optionsBuilder.timeUnit(options.getTimeUnit());
        optionsBuilder.forks(options.getForks());
        optionsBuilder.jvmArgsAppend(jvmArgs.toArray(new String[0]));
        optionsBuilder.shouldDoGC(true);
            
        // Add benchmark patterns
        for (String pattern : options.getBenchmarkPatterns()) {
            optionsBuilder.include(pattern);
        }
        
        // Add GC profiler if requested
        if (options.isIncludeGCProfiler()) {
            optionsBuilder.addProfiler(GCProfiler.class);
        }
        
        Options jmhOptions = optionsBuilder.build();
        Runner runner = new Runner(jmhOptions);
        
        return runner.run();
    }
    
    /**
     * Check if a GC is available in the current JVM
     */
    private static boolean isGCAvailable(GCConfig gcConfig) {
        try {
            // Try to create a process with the GC options to test availability
            List<String> command = new ArrayList<>();
            command.add(System.getProperty("java.home") + "/bin/java");
            command.addAll(gcConfig.getJvmArgs());
            command.add("-version");
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            int exitCode = process.waitFor();
            return exitCode == 0;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Generate comprehensive comparison reports
     */
    private static void generateComparisonReports(Map<String, Collection<RunResult>> allResults, String timestamp) 
            throws IOException {
        
        System.out.println("📊 Generating comparison reports...");
        
        Path logDir = Paths.get(LOG_DIR);
        
        // Generate detailed comparison report
        Path detailedReport = logDir.resolve("gc_comparison_detailed_" + timestamp + ".md");
        generateDetailedMarkdownReport(allResults, detailedReport);
        
        // Generate summary CSV
        Path csvReport = logDir.resolve("gc_comparison_summary_" + timestamp + ".csv");
        generateCSVReport(allResults, csvReport);
        
        // Generate console summary
        printConsoleSummary(allResults);
        
        System.out.println("📄 Reports generated:");
        System.out.println("   📊 Detailed: " + detailedReport.toAbsolutePath());
        System.out.println("   📈 CSV: " + csvReport.toAbsolutePath());
        System.out.println();
    }
    
    /**
     * Generate separate JSON reports for each GC for direct comparison
     */
    private static void generateSeparateJSONReports(Map<String, Collection<RunResult>> allResults, String timestamp) 
            throws IOException {
        
        System.out.println("📄 Generating separate JSON reports for direct comparison...");
        
        Path logDir = Paths.get(LOG_DIR);
        
        // Generate separate JSON report for each GC
        for (Map.Entry<String, Collection<RunResult>> entry : allResults.entrySet()) {
            String gcName = entry.getKey();
            Collection<RunResult> results = entry.getValue();
            
            Path jsonReport = logDir.resolve("gc_" + gcName.toLowerCase() + "_results_" + timestamp + ".json");
            
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"gc\": \"").append(gcName).append("\",\n");
            json.append("  \"timestamp\": \"").append(timestamp).append("\",\n");
            json.append("  \"benchmarks\": [\n");
            
            boolean first = true;
            for (RunResult result : results) {
                if (!first) {
                    json.append(",\n");
                }
                first = false;
                
                json.append("    {\n");
                json.append("      \"name\": \"").append(result.getPrimaryResult().getLabel()).append("\",\n");
                json.append("      \"mode\": \"").append(result.getParams().getMode()).append("\",\n");
                json.append("      \"score\": ").append(String.format("%.3f", result.getPrimaryResult().getScore())).append(",\n");
                json.append("      \"scoreError\": ").append(String.format("%.3f", result.getPrimaryResult().getScoreError())).append(",\n");
                json.append("      \"scoreUnit\": \"").append(result.getPrimaryResult().getScoreUnit()).append("\",\n");
                json.append("      \"secondaryMetrics\": {\n");
                
                boolean firstMetric = true;
                for (Map.Entry<String, org.openjdk.jmh.results.Result> metric : result.getSecondaryResults().entrySet()) {
                    if (!firstMetric) {
                        json.append(",\n");
                    }
                    firstMetric = false;
                    json.append("        \"").append(metric.getKey()).append("\": ").append(String.format("%.3f", metric.getValue().getScore()));
                }
                
                json.append("\n      }\n");
                json.append("    }");
            }
            
            json.append("\n  ]\n");
            json.append("}\n");
            
            Files.writeString(jsonReport, json.toString());
            System.out.println("   📄 " + gcName + " JSON: " + jsonReport.toAbsolutePath());
        }
        
        System.out.println();
    }
    
    /**
     * Generate combined JSON report for jmh.morethan.io
     */
    private static void generateJSONReport(Map<String, Collection<RunResult>> allResults, String timestamp) 
            throws IOException {
        
        System.out.println("📄 Generating combined JSON report for jmh.morethan.io...");
        
        Path logDir = Paths.get(LOG_DIR);
        Path jsonReport = logDir.resolve("gc_comparison_results_" + timestamp + ".json");
        
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"benchmarks\": [\n");
        
        boolean first = true;
        for (Map.Entry<String, Collection<RunResult>> entry : allResults.entrySet()) {
            String gcName = entry.getKey();
            for (RunResult result : entry.getValue()) {
                if (!first) {
                    json.append(",\n");
                }
                first = false;
                
                json.append("    {\n");
                json.append("      \"name\": \"").append(result.getPrimaryResult().getLabel()).append("\",\n");
                json.append("      \"params\": {\n");
                json.append("        \"gc\": \"").append(gcName).append("\"\n");
                json.append("      },\n");
                json.append("      \"gc\": \"").append(gcName).append("\",\n");
                json.append("      \"score\": ").append(String.format("%.3f", result.getPrimaryResult().getScore())).append(",\n");
                json.append("      \"scoreError\": ").append(String.format("%.3f", result.getPrimaryResult().getScoreError())).append(",\n");
                json.append("      \"scoreUnit\": \"").append(result.getPrimaryResult().getScoreUnit()).append("\",\n");
                json.append("      \"secondaryMetrics\": {\n");
                
                boolean firstMetric = true;
                for (Map.Entry<String, org.openjdk.jmh.results.Result> metric : result.getSecondaryResults().entrySet()) {
                    if (!firstMetric) {
                        json.append(",\n");
                    }
                    firstMetric = false;
                    json.append("        \"").append(metric.getKey()).append("\": ").append(String.format("%.3f", metric.getValue().getScore()));
                }
                
                json.append("\n      }\n");
                json.append("    }");
            }
        }
        
        json.append("\n  ]\n");
        json.append("}\n");
        
        Files.writeString(jsonReport, json.toString());
        
        System.out.println("   📄 Combined JSON: " + jsonReport.toAbsolutePath());
        System.out.println();
    }
    
    /**
     * Generate detailed markdown report
     */
    private static void generateDetailedMarkdownReport(Map<String, Collection<RunResult>> allResults, Path outputPath) 
            throws IOException {
        
        StringBuilder report = new StringBuilder();
        report.append("# GC Performance Comparison Report\n\n");
        report.append("Generated: ").append(LocalDateTime.now()).append("\n");
        report.append("JVM: ").append(System.getProperty("java.version")).append("\n\n");
        
        // Summary table
        report.append("## Performance Summary\n\n");
        report.append("| GC | Benchmark | Score | Unit | Error | GC Alloc Rate | GC Count | GC Time |\n");
        report.append("|----|-----------|-----------|-----------|-----------|-----------|-----------|-----------|\n");
        
        for (Map.Entry<String, Collection<RunResult>> entry : allResults.entrySet()) {
            String gcName = entry.getKey();
            for (RunResult result : entry.getValue()) {
                String benchmark = result.getPrimaryResult().getLabel();
                double score = result.getPrimaryResult().getScore();
                String unit = result.getPrimaryResult().getScoreUnit();
                double error = result.getPrimaryResult().getScoreError();
                
                report.append(String.format("| %s | %s | %.2f | %s | %.2f | | | |\n",
                    gcName, benchmark, score, unit, error));
            }
        }
        
        // Detailed results for each GC
        report.append("\n## Detailed Results\n\n");
        for (Map.Entry<String, Collection<RunResult>> entry : allResults.entrySet()) {
            String gcName = entry.getKey();
            report.append("### ").append(gcName).append("\n\n");
            
            for (RunResult result : entry.getValue()) {
                report.append("#### ").append(result.getPrimaryResult().getLabel()).append("\n\n");
                report.append("- **Score**: ").append(String.format("%.2f", result.getPrimaryResult().getScore()))
                      .append(" ").append(result.getPrimaryResult().getScoreUnit()).append("\n");
                report.append("- **Error**: ±").append(String.format("%.2f", result.getPrimaryResult().getScoreError())).append("\n");
                
                // Add secondary results if available
                result.getSecondaryResults().forEach((key, value) -> {
                    report.append("- **").append(key).append("**: ")
                          .append(String.format("%.2f", value.getScore()))
                          .append(" ").append(value.getScoreUnit()).append("\n");
                });
                
                report.append("\n");
            }
        }
        
        Files.writeString(outputPath, report.toString());
    }
    
    /**
     * Generate CSV report for data analysis
     */
    private static void generateCSVReport(Map<String, Collection<RunResult>> allResults, Path outputPath) 
            throws IOException {
        
        StringBuilder csv = new StringBuilder();
        csv.append("GC,Benchmark,Score,Unit,Error,Mode\n");
        
        for (Map.Entry<String, Collection<RunResult>> entry : allResults.entrySet()) {
            String gcName = entry.getKey();
            for (RunResult result : entry.getValue()) {
                csv.append(String.format("%s,%s,%.6f,%s,%.6f,%s\n",
                    gcName,
                    result.getPrimaryResult().getLabel(),
                    result.getPrimaryResult().getScore(),
                    result.getPrimaryResult().getScoreUnit(),
                    result.getPrimaryResult().getScoreError(),
                    result.getParams().getMode()
                ));
            }
        }
        
        Files.writeString(outputPath, csv.toString());
    }
    
    /**
     * Print console summary
     */
    private static void printConsoleSummary(Map<String, Collection<RunResult>> allResults) {
        System.out.println("🏆 BENCHMARK RESULTS SUMMARY");
        System.out.println("=".repeat(60));
        
        // Group results by benchmark name
        Map<String, Map<String, Double>> benchmarkScores = new LinkedHashMap<>();
        
        for (Map.Entry<String, Collection<RunResult>> entry : allResults.entrySet()) {
            String gcName = entry.getKey();
            for (RunResult result : entry.getValue()) {
                String benchmarkName = result.getPrimaryResult().getLabel();
                double score = result.getPrimaryResult().getScore();
                
                benchmarkScores.computeIfAbsent(benchmarkName, k -> new LinkedHashMap<>())
                              .put(gcName, score);
            }
        }
        
        // Print results for each benchmark
        for (Map.Entry<String, Map<String, Double>> entry : benchmarkScores.entrySet()) {
            String benchmarkName = entry.getKey();
            Map<String, Double> scores = entry.getValue();
            
            System.out.println("\n📊 " + benchmarkName);
            System.out.println("-".repeat(40));
            
            // Find best performer
            String bestGC = scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Unknown");
            
            for (Map.Entry<String, Double> scoreEntry : scores.entrySet()) {
                String gcName = scoreEntry.getKey();
                double score = scoreEntry.getValue();
                String indicator = gcName.equals(bestGC) ? " 🏆" : "";
                
                System.out.printf("  %-20s: %10.2f ops/s%s\n", gcName, score, indicator);
            }
        }
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("✅ Benchmark suite completed successfully!");
    }
    
    /**
     * Run quick benchmark for testing
     */
    public static void runQuickBenchmark() throws Exception {
        System.out.println("🚀 Running quick GC benchmark (reduced iterations)...\n");
        
        BenchmarkOptions quickOptions = new BenchmarkOptions()
            .warmupIterations(1)
            .measurementIterations(2)
            .warmupTime(5)
            .measurementTime(10)
            .benchmarkPatterns(".*memoryAllocation.*");
        
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        Map<String, Collection<RunResult>> results = new LinkedHashMap<>();
        
        // Test only G1GC and ZGC for quick feedback
        List<GCConfig> quickConfigs = Arrays.asList(
            GC_CONFIGURATIONS.get(0), // G1GC
            GC_CONFIGURATIONS.get(1)  // ZGC
        );
        
        for (GCConfig gcConfig : quickConfigs) {
            if (isGCAvailable(gcConfig)) {
                System.out.println("Testing " + gcConfig.getName() + "...");
                try {
                    Collection<RunResult> result = runBenchmarkWithGC(gcConfig, quickOptions);
                    results.put(gcConfig.getName(), result);
                } catch (Exception e) {
                    System.err.println("Failed: " + e.getMessage());
                }
            }
        }
        
        if (!results.isEmpty()) {
            printConsoleSummary(results);
            generateJSONReport(results, timestamp);
            generateSeparateJSONReports(results, timestamp);
        }
    }
    
    /**
     * Main method
     */
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "quick".equals(args[0])) {
            runQuickBenchmark();
        } else {
            runCompleteBenchmarkSuite();
        }
    }
}