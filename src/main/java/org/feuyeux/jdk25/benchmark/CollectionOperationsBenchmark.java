package org.feuyeux.jdk25.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Benchmark for testing collection operations and their GC impact.
 * Tests various collection scenarios that stress garbage collection differently.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class CollectionOperationsBenchmark extends BenchmarkBase {
    
    private static final int COLLECTION_SIZE = 50000;
    private static final int OPERATIONS = 10000;
    
    private List<Integer> testList;
    private Map<String, Integer> testMap;
    private Set<String> testSet;
    private Random random;
    
    @Setup
    public void setup() {
        random = new Random(42);
        
        // Pre-populate collections
        testList = new ArrayList<>(COLLECTION_SIZE);
        testMap = new HashMap<>(COLLECTION_SIZE);
        testSet = new HashSet<>(COLLECTION_SIZE);
        
        for (int i = 0; i < COLLECTION_SIZE; i++) {
            testList.add(random.nextInt(100000));
            testMap.put("key_" + i, i);
            testSet.add("element_" + i);
        }
    }
    
    /**
     * Test ArrayList operations: add, remove, search
     */
    @Benchmark
    public void arrayListOperations(Blackhole bh) {
        MemoryStats before = getCurrentMemoryStats();
        
        List<Integer> workingList = new ArrayList<>(testList);
        
        // Add operations
        for (int i = 0; i < OPERATIONS; i++) {
            workingList.add(random.nextInt(100000));
        }
        
        // Search operations
        for (int i = 0; i < OPERATIONS / 10; i++) {
            int searchValue = random.nextInt(100000);
            bh.consume(workingList.contains(searchValue));
        }
        
        // Remove operations
        for (int i = 0; i < OPERATIONS / 2; i++) {
            if (!workingList.isEmpty()) {
                workingList.remove(random.nextInt(workingList.size()));
            }
        }
        
        bh.consume(workingList);
        
        MemoryStats after = getCurrentMemoryStats();
        System.out.println("ArrayList operations - Memory delta: " + 
            formatBytes(after.heapUsed - before.heapUsed));
    }
    
    /**
     * Test LinkedList operations: add, remove, iteration
     */
    @Benchmark
    public void linkedListOperations(Blackhole bh) {
        MemoryStats before = getCurrentMemoryStats();
        
        LinkedList<Integer> workingList = new LinkedList<>(testList);
        
        // Add at beginning and end
        for (int i = 0; i < OPERATIONS; i++) {
            if (i % 2 == 0) {
                workingList.addFirst(random.nextInt(100000));
            } else {
                workingList.addLast(random.nextInt(100000));
            }
        }
        
        // Iterate and collect
        List<Integer> filtered = workingList.stream()
            .filter(x -> x % 2 == 0)
            .limit(OPERATIONS / 2)
            .collect(Collectors.toList());
        
        // Remove from middle
        for (int i = 0; i < OPERATIONS / 4; i++) {
            if (!workingList.isEmpty()) {
                workingList.remove(workingList.size() / 2);
            }
        }
        
        bh.consume(workingList);
        bh.consume(filtered);
        
        MemoryStats after = getCurrentMemoryStats();
        System.out.println("LinkedList operations - Memory delta: " + 
            formatBytes(after.heapUsed - before.heapUsed));
    }
    
    /**
     * Test HashMap operations: put, get, remove, iteration
     */
    @Benchmark
    public void hashMapOperations(Blackhole bh) {
        MemoryStats before = getCurrentMemoryStats();
        
        Map<String, Integer> workingMap = new HashMap<>(testMap);
        
        // Put operations
        for (int i = 0; i < OPERATIONS; i++) {
            workingMap.put("new_key_" + i, random.nextInt(100000));
        }
        
        // Get operations
        for (int i = 0; i < OPERATIONS; i++) {
            String key = "key_" + random.nextInt(COLLECTION_SIZE);
            bh.consume(workingMap.get(key));
        }
        
        // Iterate and transform
        Map<String, String> transformed = workingMap.entrySet().stream()
            .filter(entry -> entry.getValue() % 2 == 0)
            .limit(OPERATIONS / 2)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> "value_" + entry.getValue()
            ));
        
        // Remove operations
        for (int i = 0; i < OPERATIONS / 2; i++) {
            workingMap.remove("new_key_" + i);
        }
        
        bh.consume(workingMap);
        bh.consume(transformed);
        
        MemoryStats after = getCurrentMemoryStats();
        System.out.println("HashMap operations - Memory delta: " + 
            formatBytes(after.heapUsed - before.heapUsed));
    }
    
    /**
     * Test concurrent collection operations
     */
    @Benchmark
    public void concurrentMapOperations(Blackhole bh) {
        MemoryStats before = getCurrentMemoryStats();
        
        ConcurrentHashMap<String, Integer> concurrentMap = new ConcurrentHashMap<>();
        
        // Populate concurrently (simulated)
        IntStream.range(0, OPERATIONS).parallel().forEach(i -> {
            concurrentMap.put("concurrent_key_" + i, random.nextInt(100000));
        });
        
        // Concurrent reads and writes
        IntStream.range(0, OPERATIONS).parallel().forEach(i -> {
            // Read
            String readKey = "concurrent_key_" + random.nextInt(OPERATIONS);
            Integer value = concurrentMap.get(readKey);
            
            // Write
            if (value != null) {
                concurrentMap.put("modified_" + readKey, value * 2);
            }
        });
        
        // Stream operations
        List<String> processedKeys = concurrentMap.entrySet().parallelStream()
            .filter(entry -> entry.getValue() > 50000)
            .map(entry -> entry.getKey().toUpperCase())
            .collect(Collectors.toList());
        
        bh.consume(concurrentMap);
        bh.consume(processedKeys);
        
        MemoryStats after = getCurrentMemoryStats();
        System.out.println("ConcurrentMap operations - Memory delta: " + 
            formatBytes(after.heapUsed - before.heapUsed));
    }
    
    /**
     * Test collection copying and bulk operations
     */
    @Benchmark
    public void collectionCopyingOperations(Blackhole bh) {
        MemoryStats before = getCurrentMemoryStats();
        
        // Multiple collection copies
        List<Integer> copy1 = new ArrayList<>(testList);
        List<Integer> copy2 = new LinkedList<>(testList);
        Set<Integer> setFromList = new HashSet<>(testList);
        
        // Bulk operations
        copy1.addAll(copy2);
        copy1.removeAll(setFromList.stream().limit(1000).collect(Collectors.toList()));
        
        // Cross-collection operations
        Map<Integer, String> indexMap = IntStream.range(0, Math.min(testList.size(), OPERATIONS))
            .boxed()
            .collect(Collectors.toMap(
                i -> i,  // Use index as key instead of testList.get(i) to avoid duplicates
                i -> "mapped_" + i
            ));
        
        // Create nested collections
        List<List<Integer>> nestedList = new ArrayList<>();
        for (int i = 0; i < OPERATIONS / 100; i++) {
            List<Integer> subList = testList.subList(
                random.nextInt(testList.size() / 2),
                random.nextInt(testList.size() / 2) + testList.size() / 2
            );
            nestedList.add(new ArrayList<>(subList));
        }
        
        bh.consume(copy1);
        bh.consume(copy2);
        bh.consume(setFromList);
        bh.consume(indexMap);
        bh.consume(nestedList);
        
        MemoryStats after = getCurrentMemoryStats();
        System.out.println("Collection copying - Memory delta: " + 
            formatBytes(after.heapUsed - before.heapUsed));
    }
    
    /**
     * Test memory-intensive collection operations with high GC pressure
     */
    @Benchmark
    public void highMemoryPressureCollections(Blackhole bh) {
        MemoryStats before = getCurrentMemoryStats();
        
        // Create memory pressure
        createMemoryPressure(50);
        
        // Large collections with frequent modifications
        List<byte[]> binaryDataList = new ArrayList<>();
        Map<String, byte[]> binaryDataMap = new HashMap<>();
        
        for (int i = 0; i < OPERATIONS / 10; i++) {
            byte[] data = new byte[random.nextInt(8192) + 1024]; // 1-9KB
            random.nextBytes(data);
            
            binaryDataList.add(data);
            binaryDataMap.put("binary_" + i, data);
            
            // Occasionally clear some data to create fragmentation
            if (i > 0 && i % 100 == 0) {
                // Remove some old entries
                for (int j = 0; j < 10 && !binaryDataList.isEmpty(); j++) {
                    binaryDataList.remove(random.nextInt(binaryDataList.size()));
                }
                // Clear some map entries
                binaryDataMap.remove("binary_" + (i - random.nextInt(50)));
            }
        }
        
        // Process the collections
        long totalSize = binaryDataList.stream()
            .mapToLong(arr -> arr.length)
            .sum();
        
        bh.consume(binaryDataList);
        bh.consume(binaryDataMap);
        bh.consume(totalSize);
        
        MemoryStats after = getCurrentMemoryStats();
        System.out.println("High memory pressure - Memory delta: " + 
            formatBytes(after.heapUsed - before.heapUsed));
    }
}