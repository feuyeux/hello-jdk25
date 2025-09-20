package org.feuyeux.jdk25.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import java.lang.management.MemoryMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.concurrent.TimeUnit;

/**
 * Base class for all GC performance benchmarks.
 * Provides common configuration and memory monitoring utilities.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = {"-Xmx512m", "-Xms128m"})
public abstract class BenchmarkBase {
    
    protected static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    
    /**
     * Memory statistics container
     */
    public static class MemoryStats {
        public final long heapUsed;
        public final long heapCommitted;
        public final long heapMax;
        public final long nonHeapUsed;
        public final long nonHeapCommitted;
        public final long timestamp;
        
        public MemoryStats() {
            this.timestamp = System.currentTimeMillis();
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
            
            this.heapUsed = heapUsage.getUsed();
            this.heapCommitted = heapUsage.getCommitted();
            this.heapMax = heapUsage.getMax();
            this.nonHeapUsed = nonHeapUsage.getUsed();
            this.nonHeapCommitted = nonHeapUsage.getCommitted();
        }
        
        @Override
        public String toString() {
            return String.format("Heap: %d/%d/%d KB, NonHeap: %d/%d KB", 
                heapUsed/1024, heapCommitted/1024, heapMax/1024,
                nonHeapUsed/1024, nonHeapCommitted/1024);
        }
    }
    
    /**
     * Force garbage collection and return memory statistics
     */
    protected MemoryStats forceGCAndGetStats() {
        System.gc();
        System.gc(); // Call twice to ensure full collection
        try {
            Thread.sleep(100); // Give GC time to complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new MemoryStats();
    }
    
    /**
     * Get current memory statistics without forcing GC
     */
    protected MemoryStats getCurrentMemoryStats() {
        return new MemoryStats();
    }
    
    /**
     * Create memory pressure by allocating and immediately releasing objects
     */
    protected void createMemoryPressure(int iterations) {
        for (int i = 0; i < iterations; i++) {
            // Allocate various object types to stress different memory regions
            Object[] array = new Object[1000];
            String[] strings = new String[500];
            for (int j = 0; j < 500; j++) {
                strings[j] = "Memory pressure string " + i + "_" + j;
            }
            // Let objects become eligible for collection
            array = null;
            strings = null;
        }
    }
    
    /**
     * Run a specific benchmark class with JMH
     */
    public static void runBenchmark(Class<?> benchmarkClass) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(benchmarkClass.getSimpleName())
                .shouldFailOnError(true)
                .shouldDoGC(true)
                .build();
        
        new Runner(opt).run();
    }
    
    /**
     * Utility method to format bytes as human-readable string
     */
    protected static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}