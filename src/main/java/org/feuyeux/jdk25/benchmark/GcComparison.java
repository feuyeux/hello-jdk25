package org.feuyeux.jdk25.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 统一的GC对比基准测试。
 * 每个 @Benchmark 方法代表一种GC配置，通过 @Fork 注解指定JVM参数。
 * 运行此类将生成一个包含所有GC对比数据的JSON文件。
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 5, time = 10)
public class GcComparison {

    @Param({"1024", "4096"})
    private int objectSize;

    @Param({"10000"})
    private int allocationsPerIteration;

    private Random random;

    @Setup
    public void setup() {
        random = new Random();
    }

    private void allocate(Blackhole bh) {
        for (int i = 0; i < allocationsPerIteration; i++) {
            byte[] data = new byte[objectSize];
            random.nextBytes(data);
            bh.consume(data);
        }
    }

    @Benchmark
    @Fork(value = 1, jvmArgs = "-XX:+UseG1GC")
    public void g1(Blackhole bh) {
        allocate(bh);
    }

    @Benchmark
    @Fork(value = 1, jvmArgs = "-XX:+UseZGC")
    public void zgc(Blackhole bh) {
        allocate(bh);
    }

    @Benchmark
    @Fork(value = 1, jvmArgs = "-XX:+UseShenandoahGC")
    public void shenandoah(Blackhole bh) {
        allocate(bh);
    }

    /**
     * 运行所有GC对比测试，并生成一个统一的JSON报告。
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(GcComparison.class.getSimpleName())
                .addProfiler(GCProfiler.class)
                .resultFormat(ResultFormatType.JSON)
                .result("target/gc-comparison-results.json")
                .build();

        new Runner(opt).run();
    }
}
