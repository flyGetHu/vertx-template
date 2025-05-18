#!/bin/bash
echo "使用UTF-8编码启动应用..."
java -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -jar ./target/template-1.0.0-SNAPSHOT-fat.jar
