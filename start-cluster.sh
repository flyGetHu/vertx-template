#!/bin/bash

echo "启动Vert.x应用 - 集群模式"
export JAVA_OPTS="-Xms256m -Xmx512m"

# 构建项目
mvn clean package -DskipTests

# 启动Vert.x应用（集群模式）
java -Dcluster=true -jar target/template-1.0.0-SNAPSHOT-fat.jar
