#!/bin/bash
echo "Running benchmarks on all servers..."

# 모든 서버가 실행 중인지 확인
echo "Checking server availability..."

# Traditional Server 체크
curl -f http://localhost:8080/health >/dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "Warning: Traditional Server (8080) not responding"
fi

# Hybrid Server 체크
curl -f http://localhost:8081/health >/dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "Warning: Hybrid Server (8081) not responding"
fi

# Event Loop Server 체크
curl -f http://localhost:8082/health >/dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "Warning: Event Loop Server (8082) not responding"
fi

# 벤치마크 실행
java -cp build/classes:lib/* \
     -Dbenchmark.config=config/benchmark.properties \
     com.serverarch.benchmark.BenchmarkRunner

echo "Benchmark completed! Check benchmarks/reports/ for results."