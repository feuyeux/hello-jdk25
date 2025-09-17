#!/bin/bash
export JAVA_HOME=$HOME/zoo/jdk-25.jdk/Contents/Home

# 抑制sun.misc.Unsafe相关警告的Maven选项
export MAVEN_OPTS="--add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED"

# 使用grep过滤掉sun.misc.Unsafe相关警告，但保留其他输出
mvn compile -q 2>&1 | grep -v "WARNING.*sun.misc.Unsafe\|WARNING.*staticFieldBase\|WARNING.*HiddenClassDefiner" || true

"$JAVA_HOME/bin/java" --enable-preview --add-modules=jdk.incubator.vector -cp target/classes org.feuyeux.jdk25.App