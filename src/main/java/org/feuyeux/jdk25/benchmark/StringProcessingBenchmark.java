package org.feuyeux.jdk25.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Benchmark for testing string processing operations and their memory impact on GC.
 * Tests various string manipulation scenarios that create different memory pressure patterns.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class StringProcessingBenchmark extends BenchmarkBase {
    
    private static final int STRING_OPERATIONS = 10000;
    private static final String BASE_STRING = "The quick brown fox jumps over the lazy dog. ";
    private static final String[] WORDS = {
        "performance", "benchmark", "garbage", "collection", "memory", "allocation",
        "string", "processing", "concatenation", "manipulation", "optimization",
        "efficiency", "throughput", "latency", "scalability", "reliability"
    };
    
    private List<String> testStrings;
    private Random random;
    
    @Setup
    public void setup() {
        random = new Random(42);
        testStrings = new ArrayList<>(STRING_OPERATIONS);
        
        // Pre-generate test strings
        for (int i = 0; i < STRING_OPERATIONS; i++) {
            StringBuilder sb = new StringBuilder();
            int wordCount = random.nextInt(10) + 5; // 5-15 words
            for (int j = 0; j < wordCount; j++) {
                sb.append(WORDS[random.nextInt(WORDS.length)]).append(" ");
            }
            testStrings.add(sb.toString().trim());
        }
    }
    
    /**
     * Test string concatenation using + operator (creates many temporary objects)
     */
    @Benchmark
    public void stringConcatenation(Blackhole bh) {
        MemoryStats before = getCurrentMemoryStats();
        
        String result = "";
        for (int i = 0; i < STRING_OPERATIONS / 10; i++) {
            result = result + BASE_STRING + i + " ";
        }
        
        bh.consume(result);
        
        MemoryStats after = getCurrentMemoryStats();
        System.out.println("String concatenation - Memory delta: " + 
            formatBytes(after.heapUsed - before.heapUsed));
    }
    
    /**
     * Test StringBuilder operations (more memory efficient)
     */
    @Benchmark
    public void stringBuilderOperations(Blackhole bh) {
        MemoryStats before = getCurrentMemoryStats();
        
        StringBuilder sb = new StringBuilder(STRING_OPERATIONS * 50); // Pre-size
        for (int i = 0; i < STRING_OPERATIONS; i++) {
            sb.append(BASE_STRING).append(i).append(" ");
        }
        String result = sb.toString();
        
        bh.consume(result);
        
        MemoryStats after = getCurrentMemoryStats();
        System.out.println("StringBuilder operations - Memory delta: " + 
            formatBytes(after.heapUsed - before.heapUsed));
    }
    
    /**
     * Test string splitting and joining operations
     */
    @Benchmark
    public void stringSplitAndJoin(Blackhole bh) {
        MemoryStats before = getCurrentMemoryStats();
        
        List<String> processedStrings = new ArrayList<>();
        
        for (String testString : testStrings) {
            // Split string into words
            String[] words = testString.split(" ");
            
            // Process each word
            List<String> processedWords = Arrays.stream(words)
                .map(String::toUpperCase)
                .map(word -> word + "_processed")
                .collect(Collectors.toList());
            
            // Join back together
            String rejoined = String.join("-", processedWords);
            processedStrings.add(rejoined);
        }
        
        bh.consume(processedStrings);
        
        MemoryStats after = getCurrentMemoryStats();
        System.out.println("String split/join - Memory delta: " + 
            formatBytes(after.heapUsed - before.heapUsed));
    }
    
    /**
     * Test regex operations and string replacement
     */
    @Benchmark
    public void regexAndReplacement(Blackhole bh) {
        MemoryStats before = getCurrentMemoryStats();
        
        List<String> processedStrings = new ArrayList<>();
        
        for (String testString : testStrings) {
            // Multiple regex operations
            String processed = testString
                .replaceAll("\\b\\w{1,3}\\b", "***")  // Replace short words
                .replaceAll("\\s+", "_")              // Replace spaces with underscores
                .replaceAll("[aeiou]", "@")           // Replace vowels
                .toLowerCase();
            
            processedStrings.add(processed);
        }
        
        bh.consume(processedStrings);
        
        MemoryStats after = getCurrentMemoryStats();
        System.out.println("Regex operations - Memory delta: " + 
            formatBytes(after.heapUsed - before.heapUsed));
    }
    
    /**
     * Test string interning and deduplication
     */
    @Benchmark
    public void stringInterningOperations(Blackhole bh) {
        MemoryStats before = getCurrentMemoryStats();
        
        Set<String> uniqueStrings = new HashSet<>();
        List<String> internedStrings = new ArrayList<>();
        
        for (int i = 0; i < STRING_OPERATIONS; i++) {
            // Create duplicate strings that could benefit from interning
            String duplicate = new String(WORDS[i % WORDS.length]);
            String interned = duplicate.intern();
            
            uniqueStrings.add(duplicate);
            internedStrings.add(interned);
        }
        
        bh.consume(uniqueStrings);
        bh.consume(internedStrings);
        
        MemoryStats after = getCurrentMemoryStats();
        System.out.println("String interning - Memory delta: " + 
            formatBytes(after.heapUsed - before.heapUsed));
    }
    
    /**
     * Test substring operations and potential memory leaks
     */
    @Benchmark
    public void substringOperations(Blackhole bh) {
        MemoryStats before = getCurrentMemoryStats();
        
        List<String> substrings = new ArrayList<>();
        
        for (String testString : testStrings) {
            if (testString.length() > 10) {
                // Extract various substrings
                String prefix = testString.substring(0, Math.min(5, testString.length()));
                String middle = testString.substring(
                    testString.length() / 4, 
                    3 * testString.length() / 4
                );
                String suffix = testString.substring(Math.max(0, testString.length() - 5));
                
                // Create new strings to avoid potential memory leaks from substring
                substrings.add(new String(prefix));
                substrings.add(new String(middle));
                substrings.add(new String(suffix));
            }
        }
        
        bh.consume(substrings);
        
        MemoryStats after = getCurrentMemoryStats();
        System.out.println("Substring operations - Memory delta: " + 
            formatBytes(after.heapUsed - before.heapUsed));
    }
    
    /**
     * Test String.format operations which create temporary objects
     */
    @Benchmark
    public void stringFormattingOperations(Blackhole bh) {
        MemoryStats before = getCurrentMemoryStats();
        
        List<String> formattedStrings = new ArrayList<>();
        
        for (int i = 0; i < STRING_OPERATIONS; i++) {
            // Various formatting operations
            String formatted1 = String.format("Item %d: %s (%.2f%%)", 
                i, WORDS[i % WORDS.length], random.nextDouble() * 100);
            
            String formatted2 = String.format("Date: %tF, Time: %tT, Value: %,d", 
                new Date(), new Date(), random.nextInt(1000000));
            
            String formatted3 = String.format("Padded: |%10s| |%-10s| |%010d|",
                WORDS[i % WORDS.length], WORDS[(i + 1) % WORDS.length], i);
            
            formattedStrings.add(formatted1);
            formattedStrings.add(formatted2);
            formattedStrings.add(formatted3);
        }
        
        bh.consume(formattedStrings);
        
        MemoryStats after = getCurrentMemoryStats();
        System.out.println("String formatting - Memory delta: " + 
            formatBytes(after.heapUsed - before.heapUsed));
    }
    
    /**
     * Test memory-intensive string operations with high pressure
     */
    @Benchmark
    public void highMemoryPressureStringOps(Blackhole bh) {
        MemoryStats before = getCurrentMemoryStats();
        
        // Create initial memory pressure
        createMemoryPressure(100);
        
        List<String> massiveStrings = new ArrayList<>();
        
        for (int i = 0; i < STRING_OPERATIONS / 10; i++) {
            // Create large strings
            StringBuilder largeStringBuilder = new StringBuilder();
            
            // Append many repetitions
            for (int j = 0; j < 100; j++) {
                largeStringBuilder
                    .append(BASE_STRING)
                    .append(" Iteration: ").append(i).append("_").append(j)
                    .append(" Random: ").append(random.nextInt(10000))
                    .append(" ");
            }
            
            String largeString = largeStringBuilder.toString();
            
            // Perform expensive operations on large string
            String processed = largeString
                .toUpperCase()
                .replace("THE", "***")
                .replaceAll("\\d+", "NUM");
            
            massiveStrings.add(processed);
            
            // Occasionally clear some strings to create fragmentation
            if (i > 0 && i % 20 == 0 && !massiveStrings.isEmpty()) {
                massiveStrings.remove(random.nextInt(massiveStrings.size()));
            }
        }
        
        // Final processing step
        String combined = massiveStrings.stream()
            .limit(10)
            .collect(Collectors.joining("\\n"));
        
        bh.consume(massiveStrings);
        bh.consume(combined);
        
        MemoryStats after = getCurrentMemoryStats();
        System.out.println("High pressure string ops - Memory delta: " + 
            formatBytes(after.heapUsed - before.heapUsed));
    }
}