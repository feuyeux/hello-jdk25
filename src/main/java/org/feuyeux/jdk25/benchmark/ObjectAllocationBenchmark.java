package org.feuyeux.jdk25.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.util.concurrent.TimeUnit;
import java.util.Random;

/**
 * Benchmark for testing memory allocation patterns and their impact on different GCs.
 * Tests various allocation scenarios to stress test garbage collection.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class ObjectAllocationBenchmark extends BenchmarkBase {
    
    private static final int ALLOCATION_SIZE = 10000;
    private static final int OBJECT_SIZE = 1024; // 1KB objects
    
    private Random random;
    
    @Setup
    public void setup() {
        random = new Random(42); // Fixed seed for reproducible results
    }
    
    /**
     * Test rapid allocation and deallocation of small objects
     */
    @Benchmark
    public void smallObjectAllocation(Blackhole bh) {
        MemoryStats before = getCurrentMemoryStats();
        
        for (int i = 0; i < ALLOCATION_SIZE; i++) {
            // Allocate small objects of varying sizes
            byte[] smallObject = new byte[random.nextInt(256) + 64]; // 64-320 bytes
            bh.consume(smallObject);
        }
        
        MemoryStats after = getCurrentMemoryStats();
        System.out.println("Small objects - Memory delta: " + 
            formatBytes(after.heapUsed - before.heapUsed));
    }
    
    /**
     * Test allocation of medium-sized objects
     */
    @Benchmark
    public void mediumObjectAllocation(Blackhole bh) {
        MemoryStats before = getCurrentMemoryStats();
        
        for (int i = 0; i < ALLOCATION_SIZE / 10; i++) {
            // Allocate medium objects (1-4KB)
            byte[] mediumObject = new byte[OBJECT_SIZE + random.nextInt(OBJECT_SIZE * 3)];
            bh.consume(mediumObject);
        }
        
        MemoryStats after = getCurrentMemoryStats();
        System.out.println("Medium objects - Memory delta: " + 
            formatBytes(after.heapUsed - before.heapUsed));
    }
    
    /**
     * Test allocation of large objects that may be allocated directly in old generation
     */
    @Benchmark
    public void largeObjectAllocation(Blackhole bh) {
        MemoryStats before = getCurrentMemoryStats();
        
        for (int i = 0; i < ALLOCATION_SIZE / 100; i++) {
            // Allocate large objects (64KB-256KB)
            byte[] largeObject = new byte[64 * 1024 + random.nextInt(192 * 1024)];
            bh.consume(largeObject);
        }
        
        MemoryStats after = getCurrentMemoryStats();
        System.out.println("Large objects - Memory delta: " + 
            formatBytes(after.heapUsed - before.heapUsed));
    }
    
    /**
     * Test mixed allocation pattern with different object sizes
     */
    @Benchmark
    public void mixedAllocationPattern(Blackhole bh) {
        MemoryStats before = getCurrentMemoryStats();
        
        for (int i = 0; i < ALLOCATION_SIZE; i++) {
            Object obj;
            int choice = random.nextInt(100);
            
            if (choice < 70) {
                // 70% small objects
                obj = new byte[random.nextInt(512) + 32];
            } else if (choice < 95) {
                // 25% medium objects
                obj = new byte[random.nextInt(8192) + 1024];
            } else {
                // 5% large objects
                obj = new byte[random.nextInt(65536) + 32768];
            }
            
            bh.consume(obj);
        }
        
        MemoryStats after = getCurrentMemoryStats();
        System.out.println("Mixed allocation - Memory delta: " + 
            formatBytes(after.heapUsed - before.heapUsed));
    }
    
    /**
     * Test rapid allocation with short-lived objects (high GC pressure)
     */
    @Benchmark
    public void highPressureAllocation(Blackhole bh) {
        MemoryStats before = getCurrentMemoryStats();
        
        // Create memory pressure before the main allocation
        createMemoryPressure(100);
        
        for (int i = 0; i < ALLOCATION_SIZE * 2; i++) {
            // Allocate many small objects quickly
            String str = "High pressure allocation test string " + i;
            byte[] data = new byte[random.nextInt(1024)];
            Object[] array = new Object[random.nextInt(100) + 10];
            
            bh.consume(str);
            bh.consume(data);
            bh.consume(array);
        }
        
        MemoryStats after = getCurrentMemoryStats();
        System.out.println("High pressure - Memory delta: " + 
            formatBytes(after.heapUsed - before.heapUsed));
    }
    
    /**
     * Test allocation with some long-lived objects to test generational GC behavior
     */
    @Benchmark
    public void mixedLifetimeAllocation(Blackhole bh) {
        MemoryStats before = getCurrentMemoryStats();
        
        // Some long-lived objects (will survive to old generation)
        Object[] longLived = new Object[100];
        for (int i = 0; i < longLived.length; i++) {
            longLived[i] = new byte[random.nextInt(2048) + 1024];
        }
        
        // Many short-lived objects
        for (int i = 0; i < ALLOCATION_SIZE; i++) {
            byte[] shortLived = new byte[random.nextInt(512) + 64];
            bh.consume(shortLived);
            
            // Occasionally modify long-lived objects
            if (i % 1000 == 0 && longLived.length > 0) {
                int index = random.nextInt(longLived.length);
                longLived[index] = new byte[random.nextInt(2048) + 1024];
            }
        }
        
        bh.consume(longLived);
        
        MemoryStats after = getCurrentMemoryStats();
        System.out.println("Mixed lifetime - Memory delta: " + 
            formatBytes(after.heapUsed - before.heapUsed));
    }
}