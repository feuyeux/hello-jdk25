package org.feuyeux.jdk25.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * JMH-based GC Performance Benchmark
 * Compares G1GC, ZGC, and Shenandoah garbage collectors
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 15, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
public class GCBenchmark {
    
    // Benchmark parameters
    @Param({"1024", "2048", "4096"})
    private int objectSize;
    
    @Param({"100", "1000", "10000"})
    private int allocationsPerIteration;
    
    // Shared data structures for memory pressure
    private static final ConcurrentHashMap<String, Object> CACHE = new ConcurrentHashMap<>();
    private static final List<byte[]> LARGE_OBJECTS = new CopyOnWriteArrayList<>();
    private static final AtomicLong ALLOCATION_COUNTER = new AtomicLong(0);
    
    @State(Scope.Thread)
    public static class ThreadState {
        private final Random random = new Random();
        private final List<Object> localObjects = new ArrayList<>();
    }
    
    /**
     * Memory allocation benchmark - creates objects of various sizes
     */
    @Benchmark
    public void memoryAllocation(Blackhole bh, ThreadState state) {
        for (int i = 0; i < allocationsPerIteration; i++) {
            byte[] data = new byte[objectSize];
            state.random.nextBytes(data);
            bh.consume(data);
            
            // Keep some objects alive to create memory pressure
            if (i % 10 == 0) {
                state.localObjects.add(data);
                if (state.localObjects.size() > 100) {
                    state.localObjects.remove(0);
                }
            }
        }
    }
    
    /**
     * Collection processing benchmark - simulates typical application workloads
     */
    @Benchmark
    public void collectionProcessing(Blackhole bh, ThreadState state) {
        List<Map<String, Object>> data = new ArrayList<>();
        
        // Create data structures
        for (int i = 0; i < allocationsPerIteration / 10; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", ALLOCATION_COUNTER.incrementAndGet());
            item.put("timestamp", System.currentTimeMillis());
            item.put("data", new byte[objectSize / 10]);
            item.put("metadata", Arrays.asList("tag1", "tag2", "tag3"));
            data.add(item);
        }
        
        // Process data
        List<String> results = data.parallelStream()
            .filter(item -> (Long) item.get("id") % 2 == 0)
            .map(item -> "processed_" + item.get("id"))
            .sorted()
            .toList();
        
        bh.consume(results);
    }
    
    /**
     * High allocation rate benchmark - tests GC under memory pressure
     */
    @Benchmark
    public void highAllocationRate(Blackhole bh, ThreadState state) {
        // Rapid allocation of various sized objects
        for (int i = 0; i < allocationsPerIteration; i++) {
            int size = objectSize + state.random.nextInt(objectSize);
            byte[] temp = new byte[size];
            state.random.nextBytes(temp);
            
            // Some objects go to cache (survive longer)
            if (i % 20 == 0) {
                String key = "cache_" + ALLOCATION_COUNTER.incrementAndGet();
                CACHE.put(key, temp);
                
                // Cleanup cache periodically
                if (CACHE.size() > 1000) {
                    String oldKey = "cache_" + (ALLOCATION_COUNTER.get() - 1000);
                    CACHE.remove(oldKey);
                }
            }
            
            bh.consume(temp);
        }
    }
    
    /**
     * Long-lived objects benchmark - tests generational GC behavior
     */
    @Benchmark
    public void longLivedObjects(Blackhole bh, ThreadState state) {
        // Create objects with different lifetimes
        
        // Short-lived objects (should die in young generation)
        for (int i = 0; i < allocationsPerIteration / 2; i++) {
            byte[] shortLived = new byte[objectSize / 4];
            state.random.nextBytes(shortLived);
            bh.consume(shortLived);
        }
        
        // Medium-lived objects
        List<byte[]> mediumLived = new ArrayList<>();
        for (int i = 0; i < allocationsPerIteration / 10; i++) {
            byte[] data = new byte[objectSize / 2];
            state.random.nextBytes(data);
            mediumLived.add(data);
        }
        
        // Some objects survive to become long-lived
        if (state.random.nextInt(100) < 5) {
            LARGE_OBJECTS.add(new byte[objectSize * 2]);
            
            // Cleanup old objects occasionally
            if (LARGE_OBJECTS.size() > 50) {
                LARGE_OBJECTS.remove(0);
            }
        }
        
        bh.consume(mediumLived);
    }
    
    /**
     * Concurrent access benchmark - tests GC under thread contention
     */
    @Benchmark
    @Threads(4)
    public void concurrentAccess(Blackhole bh, ThreadState state) {
        // Concurrent allocation and access patterns
        List<CompletableFuture<byte[]>> futures = new ArrayList<>();
        
        for (int i = 0; i < allocationsPerIteration / 20; i++) {
            CompletableFuture<byte[]> future = CompletableFuture.supplyAsync(() -> {
                byte[] data = new byte[objectSize];
                ThreadLocalRandom.current().nextBytes(data);
                
                // Simulate some processing time
                LockSupport.parkNanos(1000); // 1 microsecond
                
                return data;
            });
            futures.add(future);
        }
        
        // Collect all results
        List<byte[]> results = futures.stream()
            .map(CompletableFuture::join)
            .toList();
        
        bh.consume(results);
    }
    
    /**
     * Mixed workload benchmark - combines different allocation patterns
     */
    @Benchmark
    public void mixedWorkload(Blackhole bh, ThreadState state) {
        // Randomly choose workload type
        int workloadType = state.random.nextInt(4);
        
        switch (workloadType) {
            case 0 -> memoryAllocation(bh, state);
            case 1 -> {
                // Reduced iterations for collection processing
                int oldValue = allocationsPerIteration;
                allocationsPerIteration = allocationsPerIteration / 10;
                collectionProcessing(bh, state);
                allocationsPerIteration = oldValue;
            }
            case 2 -> highAllocationRate(bh, state);
            case 3 -> longLivedObjects(bh, state);
        }
    }
    
    /**
     * Setup method - runs before each benchmark
     */
    @Setup(Level.Trial)
    public void setup() {
        System.out.println("Starting GC Benchmark with object size: " + objectSize + 
                          ", allocations per iteration: " + allocationsPerIteration);
        ALLOCATION_COUNTER.set(0);
        CACHE.clear();
        LARGE_OBJECTS.clear();
    }
    
    /**
     * Teardown method - runs after each benchmark
     */
    @TearDown(Level.Trial)
    public void tearDown() {
        System.out.println("Completed benchmark. Total allocations: " + ALLOCATION_COUNTER.get());
        CACHE.clear();
        LARGE_OBJECTS.clear();
    }
    
    /**
     * Main method to run benchmarks programmatically
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(GCBenchmark.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .shouldDoGC(true)
            .build();
        
        new Runner(opt).run();
    }
    
}