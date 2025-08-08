#!/bin/bash

echo "Building JavaServerArchitectures..."

# 로그 디렉토리 생성
mkdir -p logs

# 기존 클래스 파일 정리
rm -rf build/classes/*
rm -rf build/test-classes/*

echo "Compiling main sources..."
# 메인 소스 컴파일
find src/main/java -name "*.java" > sources.list
if [ -s sources.list ]; then
    javac -d build/classes -cp "lib/*" @sources.list
    if [ $? -eq 0 ]; then
        echo "Main compilation successful!"
    else
        echo "Main compilation failed!"
        exit 1
    fi
else
    echo "No main source files found."
fi

echo "Compiling test sources..."
# 테스트 소스 컴파일
find test/java -name "*.java" > test-sources.list 2>/dev/null
if [ -s test-sources.list ]; then
    javac -d build/test-classes -cp "build/classes:lib/*" @test-sources.list
    if [ $? -eq 0 ]; then
        echo "Test compilation successful!"
    else
        echo "Test compilation failed!"
        exit 1
    fi
else
    echo "No test source files found."
fi

# 임시 파일 정리
rm -f sources.list test-sources.list

echo "Build completed successfully!"