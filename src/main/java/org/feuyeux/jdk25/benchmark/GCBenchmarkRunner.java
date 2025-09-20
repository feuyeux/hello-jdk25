package org.feuyeux.jdk25.benchmark;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Main runner for GC performance benchmarks.
 * Executes all benchmark suites with different GC configurations and collects results.
 */
public class GCBenchmarkRunner {
    
    private static final String LOG_DIR = "log";
    private static final String RESULT_DIR = "result";
    private static final List<GCConfiguration> GC_CONFIGS = Arrays.asList(
        new GCConfiguration("G1 GC", "-XX:+UseG1GC"),
        new GCConfiguration("ZGC", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseZGC"),
        new GCConfiguration("ZGC Generational", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseZGC", "-XX:+UseGenerationalZGC"),
        new GCConfiguration("Shenandoah GC", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseShenandoahGC"),
        new GCConfiguration("Shenandoah Generational", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseShenandoahGC", "-XX:+UseGenerationalShenandoahGC")
    );
    
    private static final List<Class<?>> BENCHMARK_CLASSES = Arrays.asList(
        ObjectAllocationBenchmark.class,
        CollectionOperationsBenchmark.class,
        StringProcessingBenchmark.class
    );
    
    private final String timestamp;
    private final ResultsCollector resultsCollector;
    
    public GCBenchmarkRunner() {
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        this.resultsCollector = new ResultsCollector();
        
        // Ensure log and result directories exist
        try {
            Files.createDirectories(Paths.get(LOG_DIR));
            Files.createDirectories(Paths.get(RESULT_DIR));
        } catch (IOException e) {
            System.err.println("Failed to create directories: " + e.getMessage());
        }
    }
    
    /**
     * GC Configuration holder
     */
    private static class GCConfiguration {
        public final String name;
        public final String[] flags;
        
        public GCConfiguration(String name, String... flags) {
            this.name = name;
            this.flags = flags;
        }
        
        public String getFlagsAsString() {
            return String.join(" ", flags);
        }
    }
    
    /**
     * Run all benchmarks with all GC configurations
     */
    public void runAllBenchmarks() {
        System.out.println("=".repeat(80));
        System.out.println("GC ALGORITHM PERFORMANCE BENCHMARK SUITE");
        System.out.println("=".repeat(80));
        System.out.println("Timestamp: " + timestamp);
        System.out.println("JDK Version: " + System.getProperty("java.version"));
        System.out.println("Available Processors: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Max Memory: " + formatBytes(Runtime.getRuntime().maxMemory()));
        System.out.println();
        
        System.out.println("Benchmark Classes:");
        for (Class<?> benchmarkClass : BENCHMARK_CLASSES) {
            System.out.println("  - " + benchmarkClass.getSimpleName());
        }
        System.out.println();
        
        System.out.println("GC Configurations:");
        for (GCConfiguration config : GC_CONFIGS) {
            System.out.println("  - " + config.name + ": " + config.getFlagsAsString());
        }
        System.out.println();
        
        // Run benchmarks for each GC configuration
        for (GCConfiguration gcConfig : GC_CONFIGS) {
            runBenchmarksForGC(gcConfig);
        }
        
        // Generate comprehensive comparison report
        System.out.println("\\n" + "=".repeat(50));
        System.out.println("GENERATING COMPARISON REPORTS");
        System.out.println("=".repeat(50));
        resultsCollector.generateComparisonReport();
        
        System.out.println("\\n" + "=".repeat(50));
        System.out.println("BENCHMARK SUITE COMPLETED");
        System.out.println("=".repeat(50));
        System.out.println("Check the " + LOG_DIR + " directory for detailed results and reports.");
    }
    
    /**
     * Run benchmarks for a specific GC configuration
     */
    private void runBenchmarksForGC(GCConfiguration gcConfig) {
        System.out.println("\\n" + "-".repeat(60));
        System.out.println("RUNNING BENCHMARKS WITH: " + gcConfig.name);
        System.out.println("-".repeat(60));
        System.out.println("Flags: " + gcConfig.getFlagsAsString());
        System.out.println();
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Check if GC is available
            if (!isGCAvailable(gcConfig)) {
                System.out.println("❌ " + gcConfig.name + " is not available in this JDK build");
                resultsCollector.recordFailure(gcConfig.name, "GC not available in this JDK build");
                return;
            }
            
            // Create result file path
            String safeName = gcConfig.name.toLowerCase().replaceAll("[\\s\\W]+", "-");
            Path resultFile = Paths.get(LOG_DIR, "jmh_results_" + safeName + "_" + timestamp + ".txt");
            
            // Build JMH options - optimized for 5-minute execution
            Options opt = new OptionsBuilder()
                .include(".*Benchmark.*") // Include all benchmark classes
                .warmupIterations(1)  // Reduced from 3 to 1
                .measurementIterations(2)  // Reduced from 5 to 2
                .forks(1)  // Reduced from 2 to 1
                .threads(1)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                .verbosity(VerboseMode.NORMAL)
                .output(resultFile.toString())
                .jvmArgsAppend(gcConfig.flags)
                .jvmArgsAppend("-Xmx512m", "-Xms128m")
                .jvmArgsAppend("-Xlog:gc*:" + LOG_DIR + "/gc_" + safeName + "_" + timestamp + ".log:time,level,tags")
                .build();
            
            System.out.println("🚀 Starting JMH benchmarks...");
            System.out.println("📊 Results will be saved to: " + resultFile);
            System.out.println();
            
            // Run the benchmarks
            new Runner(opt).run();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            System.out.println();
            System.out.println("✅ " + gcConfig.name + " benchmarks completed successfully");
            System.out.println("⏱️  Total execution time: " + formatDuration(duration));
            
            // Parse and collect results
            resultsCollector.parseJMHResults(gcConfig.name, resultFile);
            
        } catch (RunnerException e) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            System.err.println("❌ " + gcConfig.name + " benchmarks failed: " + e.getMessage());
            System.err.println("⏱️  Failed after: " + formatDuration(duration));
            
            resultsCollector.recordFailure(gcConfig.name, e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ Unexpected error running " + gcConfig.name + ": " + e.getMessage());
            e.printStackTrace();
            resultsCollector.recordFailure(gcConfig.name, "Unexpected error: " + e.getMessage());
        }
    }
    
    /**
     * Check if a GC is available by testing the flags
     */
    private boolean isGCAvailable(GCConfiguration gcConfig) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.command("java");
            pb.command().addAll(Arrays.asList(gcConfig.flags));
            pb.command().add("-version");
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            return exitCode == 0;
        } catch (Exception e) {
            System.err.println("Error checking GC availability: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Format bytes as human-readable string
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    /**
     * Format duration as human-readable string
     */
    private static String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        
        if (minutes > 0) {
            return String.format("%d min %d sec", minutes, seconds);
        } else {
            return String.format("%d sec", seconds);
        }
    }
    
    /**
     * Run a specific benchmark class only
     */
    public void runSpecificBenchmark(Class<?> benchmarkClass) {
        System.out.println("Running specific benchmark: " + benchmarkClass.getSimpleName());
        
        for (GCConfiguration gcConfig : GC_CONFIGS) {
            if (!isGCAvailable(gcConfig)) {
                System.out.println("Skipping " + gcConfig.name + " - not available");
                continue;
            }
            
            try {
                Options opt = new OptionsBuilder()
                    .include(benchmarkClass.getSimpleName())
                    .warmupIterations(1)  // Reduced for quick execution
                    .measurementIterations(2)  // Reduced for quick execution
                    .forks(1)
                    .jvmArgsAppend(gcConfig.flags)
                    .jvmArgsAppend("-Xmx512m", "-Xms128m")
                    .build();
                
                System.out.println("\\nRunning " + benchmarkClass.getSimpleName() + " with " + gcConfig.name);
                new Runner(opt).run();
                
            } catch (RunnerException e) {
                System.err.println("Failed to run " + benchmarkClass.getSimpleName() + 
                    " with " + gcConfig.name + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Main entry point
     */
    public static void main(String[] args) {
        GCBenchmarkRunner runner = new GCBenchmarkRunner();
        
        if (args.length > 0) {
            // Run specific benchmark if class name provided
            String benchmarkName = args[0];
            Class<?> benchmarkClass = BENCHMARK_CLASSES.stream()
                .filter(clazz -> clazz.getSimpleName().equalsIgnoreCase(benchmarkName) ||
                               clazz.getSimpleName().toLowerCase().contains(benchmarkName.toLowerCase()))
                .findFirst()
                .orElse(null);
            
            if (benchmarkClass != null) {
                runner.runSpecificBenchmark(benchmarkClass);
            } else {
                System.err.println("Benchmark class not found: " + benchmarkName);
                System.err.println("Available benchmarks:");
                BENCHMARK_CLASSES.forEach(clazz -> System.err.println("  - " + clazz.getSimpleName()));
                System.exit(1);
            }
        } else {
            // Run full benchmark suite
            runner.runAllBenchmarks();
        }
    }
}