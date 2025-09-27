#!/bin/bash

# Configuration
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS="--add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED"

# GC基准测试对比脚本
# 使用不同的GC配置运行基准测试并生成对比报告

echo "=== GC 基准测试对比工具 ==="
echo "正在编译项目..."

# 编译项目
mvn clean package -DskipTests -q

if [ $? -ne 0 ]; then
    echo "❌ 编译失败"
    exit 1
fi

echo "✅ 编译成功"
echo ""

# 测试参数
DURATION="2m"
RPS="1000"
THREADS="4"
HEAP_SIZE="2g"

# 结果文件
RESULTS_DIR="gc-benchmark-results"
mkdir -p $RESULTS_DIR

echo "📊 开始GC对比测试..."
echo "测试参数: 持续时间=${DURATION}, RPS=${RPS}, 线程数=${THREADS}, 堆大小=${HEAP_SIZE}"
echo ""

# 测试G1GC
echo "🔄 测试 G1GC..."
java -jar target/benchmarks.jar ".*GCBenchmark.*" \
     -jvmArgs "-XX:+UseG1GC -Xlog:gc*:gc-benchmark-results/g1gc.log" \
     -f 1 -wi 1 -i 1 -r 10s > ${RESULTS_DIR}/g1gc_result.txt 2>&1

echo "✅ G1GC 测试完成"

# 测试ZGC (如果支持)
echo "🔄 测试 ZGC..."
java -jar target/benchmarks.jar ".*GCBenchmark.*" \
     -jvmArgs "-XX:+UnlockExperimentalVMOptions -XX:+UseZGC -Xlog:gc*:gc-benchmark-results/zgc.log" \
     -f 1 -wi 1 -i 1 -r 10s > ${RESULTS_DIR}/zgc_result.txt 2>&1

if [ $? -eq 0 ]; then
    echo "✅ ZGC 测试完成"
else
    echo "⚠️  ZGC 测试失败 (可能不支持)"
fi

# 测试Shenandoah (如果支持)
echo "🔄 测试 Shenandoah..."
java -jar target/benchmarks.jar ".*GCBenchmark.*" \
     -jvmArgs "-XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xlog:gc*:gc-benchmark-results/shenandoah.log" \
     -f 1 -wi 1 -i 1 -r 10s > ${RESULTS_DIR}/shenandoah_result.txt 2>&1

if [ $? -eq 0 ]; then
    echo "✅ Shenandoah 测试完成"
else
    echo "⚠️  Shenandoah 测试失败 (可能不支持)"
fi

echo ""
echo "📋 生成对比报告..."

# 运行对比工具
java -cp target/classes org.feuyeux.jdk25.benchmark.GCComparison analyze

echo ""
echo "📁 详细结果保存在: ${RESULTS_DIR}/"
echo "   - g1gc_result.txt: G1GC测试结果"
echo "   - zgc_result.txt: ZGC测试结果"  
echo "   - shenandoah_result.txt: Shenandoah测试结果"
echo "   - *.log: GC日志文件"

echo ""
echo "🎯 测试完成！查看上方的对比报告了解各GC的性能特点。"