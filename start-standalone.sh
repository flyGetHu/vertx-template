#!/bin/bash

echo "启动Vert.x应用 - 单机模式"
export JAVA_OPTS="-Xms256m -Xmx512m"

mvn clean package -DskipTests
java -jar target/template-1.0.0-SNAPSHOT-fat.jar
