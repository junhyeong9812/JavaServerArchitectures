# 하이브리드 서버 벤치마크 수정 완전 가이드

## 📋 수정 개요

벤치마크 실행 시 발생한 두 가지 주요 문제를 해결합니다:

1. **AdaptiveThreadPool의 ClassCastException** - `PriorityBlockingQueue`에서 `FutureTask`가 `Comparable`을 구현하지 않아 발생
2. **Hybrid Server의 404 에러** - `/hello` 경로에 대한 서블릿이 등록되지 않아 발생

## 🔧 1. AdaptiveThreadPool.java 수정

### 문제점
```
ClassCastException: FutureTask cannot be cast to Comparable
```

### 해결책
모든 작업을 `PriorityTask`로 래핑하고 `SecurityManager` 제거 (Java 17+ 호환)

```java
package server.hybrid;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;

/**
 * 적응형 스레드풀 - 하이브리드 서버의 핵심 컴포넌트 (수정된 버전)
 */
public class AdaptiveThreadPool extends ThreadPoolExecutor {
    // ... 기존 필드들 ...

    /**
     * 작업 제출 (우선순위 지원) - 수정된 버전
     */
    public Future<?> submit(Runnable task, int priority) {
        submittedTasks.incrementAndGet();

        // ⭐ 모든 작업을 PriorityTask로 래핑
        PriorityTask priorityTask = new PriorityTask(task, priority);

        try {
            execute(priorityTask);
            return priorityTask.getFuture();
        } catch (RejectedExecutionException e) {
            rejectedTasks.incrementAndGet();
            logger.warn("작업 거부 - 스레드풀: {}, 우선순위: {}", poolName, priority);
            throw e;
        }
    }

    /**
     * 일반 작업 제출 - 수정된 버전
     */
    @Override
    public Future<?> submit(Runnable task) {
        return submit(task, 0);  // 기본 우선순위 0
    }

    /**
     * ⭐ 우선순위 작업 래퍼 클래스 - 수정된 버전
     */
    private static class PriorityTask implements Runnable, Comparable<PriorityTask> {
        private final Runnable task;
        private final int priority;
        private final long createdTime;
        private volatile long startTime;
        private final FutureTask<Void> future;

        public PriorityTask(Runnable task, int priority) {
            this.task = task;
            this.priority = priority;
            this.createdTime = System.nanoTime();
            this.future = new FutureTask<>(() -> {
                task.run();
                return null;
            });
        }

        @Override
        public void run() {
            future.run();
        }

        @Override
        public int compareTo(PriorityTask other) {
            int result = Integer.compare(other.priority, this.priority);
            if (result == 0) {
                result = Long.compare(this.createdTime, other.createdTime);
            }
            return result;
        }

        public Future<Void> getFuture() { return future; }
        // ... getters/setters ...
    }

    /**
     * 커스텀 스레드 팩토리 (Java 17+ 호환)
     */
    private static class AdaptiveThreadFactory implements ThreadFactory {
        private final String poolName;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final ThreadGroup group;

        AdaptiveThreadFactory(String poolName) {
            this.poolName = poolName;
            // ⭐ SecurityManager 제거 - Java 17+ 호환
            this.group = Thread.currentThread().getThreadGroup();
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, poolName + "-" + threadNumber.getAndIncrement(), 0);
            if (t.isDaemon()) t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY) t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
    
    // ... 나머지 코드는 기존과 동일 ...
}
```

## 🔧 2. 하이브리드 서버용 새 서블릿 파일들

### 2.1 HealthAsyncServlet.java
```java
package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * Health Check 비동기 서블릿
 */
public class HealthAsyncServlet extends MiniAsyncServlet {

    @Override
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            String healthJson = String.format(
                    "{ \"status\": \"healthy\", \"server\": \"HybridServer\", " +
                            "\"thread\": \"%s\", \"timestamp\": %d, \"processing\": \"async\" }",
                    Thread.currentThread().getName(),
                    System.currentTimeMillis()
            );
            response.sendJson(healthJson);
        });
    }

    @Override
    protected CompletableFuture<Void> doPostAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            response.sendJson(
                    "{ \"status\": \"healthy\", \"method\": \"POST\", \"server\": \"HybridServer\" }"
            );
        });
    }
}
```

### 2.2 CpuIntensiveAsyncServlet.java
```java
package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * CPU 집약적 작업 비동기 서블릿
 */
public class CpuIntensiveAsyncServlet extends MiniAsyncServlet {

    @Override
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            double result = 0;
            int iterations = 100000;
            
            // 쿼리 파라미터로 반복 횟수 조정
            String iterParam = request.getParameter("iterations");
            if (iterParam != null) {
                try {
                    iterations = Integer.parseInt(iterParam);
                    iterations = Math.min(iterations, 1000000);
                } catch (NumberFormatException e) {
                    // 기본값 사용
                }
            }
            
            // 복잡한 수학 연산
            for (int i = 0; i < iterations; i++) {
                result += Math.sqrt(i) * Math.sin(i) * Math.cos(i % 100);
                if (i % 1000 == 0) {
                    result += Math.pow(i, 0.1);
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            String resultJson = String.format(
                    "{ \"computation\": %.4f, \"duration\": %d, " +
                            "\"iterations\": %d, \"thread\": \"%s\", \"server\": \"HybridServer\", " +
                            "\"processing\": \"async\", \"timestamp\": %d }",
                    result, duration, iterations, Thread.currentThread().getName(),
                    System.currentTimeMillis()
            );
            
            response.sendJson(resultJson);
        });
    }
}
```

### 2.3 IoSimulationAsyncServlet.java
```java
package server.hybrid;

import server.core.mini.*;
import server.core.http.*;
import java.util.concurrent.CompletableFuture;
import java.util.Random;

/**
 * I/O 시뮬레이션 비동기 서블릿
 */
public class IoSimulationAsyncServlet extends MiniAsyncServlet {

    private static final Random random = new Random();

    @Override
    protected CompletableFuture<Void> doGetAsync(MiniRequest request, MiniResponse response) {
        return CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                int delayMs = 100;
                String delayParam = request.getParameter("delay");
                if (delayParam != null) {
                    try {
                        delayMs = Integer.parseInt(delayParam);
                        delayMs = Math.min(delayMs, 5000);
                    } catch (NumberFormatException e) {
                        // 기본값 사용
                    }
                }
                
                String ioType = request.getParameter("type");
                if (ioType == null) {
                    String[] types = {"database", "file", "api", "cache"};
                    ioType = types[random.nextInt(types.length)];
                }
                
                Thread.sleep(delayMs);
                String ioResult = simulateIoOperation(ioType, delayMs);
                
                long duration = System.currentTimeMillis() - startTime;
                
                String resultJson = String.format(
                        "{ \"ioType\": \"%s\", \"delay\": %d, \"duration\": %d, " +
                                "\"result\": \"%s\", \"thread\": \"%s\", \"server\": \"HybridServer\", " +
                                "\"processing\": \"async\", \"timestamp\": %d }",
                        ioType, delayMs, duration, ioResult,
                        Thread.currentThread().getName(), System.currentTimeMillis()
                );
                
                response.sendJson(resultJson);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR, "I/O simulation interrupted");
            }
        });
    }

    private String simulateIoOperation(String ioType, int baseDelay) throws InterruptedException {
        switch (ioType.toLowerCase()) {
            case "database":
                Thread.sleep(baseDelay + random.nextInt(50));
                return "Database query completed - 150 rows affected";
            case "file":
                Thread.sleep(baseDelay + random.nextInt(30));
                return "File read completed - 2.5MB processed";
            case "api":
                Thread.sleep(baseDelay + random.nextInt(100));
                return "External API call completed - 200 OK";
            case "cache":
                Thread.sleep(Math.max(5, baseDelay / 10));
                return "Cache lookup completed - Hit ratio: 85%";
            default:
                Thread.sleep(baseDelay);
                return "Generic I/O operation completed";
        }
    }
}
```

## 🔧 3. BenchmarkRunner.java 수정

### 3.1 새 메서드 추가

```java
/**
 * Hybrid Server용 서블릿 등록
 */
private void registerHybridServlets(HybridMiniServletContainer container) {
    try {
        // 1. Health Check 서블릿
        container.registerServlet("Health", 
                new server.hybrid.HealthAsyncServlet(), "/health");
        
        // 2. Hello World 서블릿 (벤치마크 핵심)
        container.registerServlet("HelloWorld", 
                new server.hybrid.HelloWorldAsyncServlet(), "/hello");
        
        // 3. CPU 집약적 작업 서블릿
        container.registerServlet("CpuIntensive", 
                new server.hybrid.CpuIntensiveAsyncServlet(), "/cpu-intensive");
        
        // 4. I/O 시뮬레이션 서블릿
        container.registerServlet("IoSimulation", 
                new server.hybrid.IoSimulationAsyncServlet(), "/io-simulation");
        
        logger.info("✅ Hybrid Server 서블릿 등록 완료 - 4개 서블릿 등록됨");
        
    } catch (Exception e) {
        logger.error("❌ Hybrid Server 서블릿 등록 실패", e);
        throw new RuntimeException("Hybrid 서블릿 등록 실패", e);
    }
}
```

### 3.2 startServers() 메서드 수정

```java
private void startServers() throws Exception {
    logger.info("Starting all servers...");

    // Threaded Server 시작
    threadedServer = new ThreadedServer(THREADED_PORT);
    registerThreadedServlets(threadedServer);
    threadedServer.start();
    logger.info("Threaded Server started on port {}", THREADED_PORT);

    // ⭐ Hybrid Server 시작 (서블릿 등록 추가)
    hybridServer = new HybridServer(HYBRID_PORT);
    registerHybridServlets(hybridServer.getServletContainer());  // ← 이 줄 추가
    setupHybridRoutes(hybridServer);
    hybridServer.start();
    logger.info("Hybrid Server started on port {}", HYBRID_PORT);

    // EventLoop Server 시작
    eventLoopServer = new EventLoopServer();
    setupEventLoopRoutes(eventLoopServer);
    eventLoopServer.start(EVENTLOOP_PORT);
    logger.info("EventLoop Server started on port {}", EVENTLOOP_PORT);
}
```

## 📁 파일 구조

수정 후 파일 구조:

```
server/
├── hybrid/
│   ├── AdaptiveThreadPool.java           ← 수정됨
│   ├── HealthAsyncServlet.java           ← 새로 생성
│   ├── CpuIntensiveAsyncServlet.java     ← 새로 생성
│   ├── IoSimulationAsyncServlet.java     ← 새로 생성
│   ├── HelloWorldAsyncServlet.java       ← 기존 파일
│   ├── MiniAsyncServlet.java             ← 기존 파일
│   └── ... (기타 하이브리드 파일들)
├── benchmark/
│   └── BenchmarkRunner.java             ← 수정됨
└── ...
```

## ⚡ 수정 적용 순서

1. **AdaptiveThreadPool.java 교체**
    - 기존 파일을 새 버전으로 교체

2. **새 서블릿 파일들 생성**
    - `HealthAsyncServlet.java`
    - `CpuIntensiveAsyncServlet.java`
    - `IoSimulationAsyncServlet.java`

3. **BenchmarkRunner.java 수정**
    - `registerHybridServlets()` 메서드 추가
    - `startServers()` 메서드에서 호출 추가

4. **컴파일 및 테스트**
   ```bash
   javac -cp . server/hybrid/*.java
   javac -cp . server/benchmark/*.java
   java server.benchmark.BenchmarkRunner
   ```

## ✅ 기대 결과

수정 후 벤치마크 실행 시:

```
✅ Hybrid Server 서블릿 등록 완료 - 4개 서블릿 등록됨
✅ All servers are ready for benchmarking

# 모든 서버에서 정상 응답
GET localhost:8080/hello → 200 OK (Threaded)
GET localhost:8081/hello → 200 OK (Hybrid)    ← 이제 정상 동작
GET localhost:8082/hello → 200 OK (EventLoop)
```

## 🐛 문제 해결 완료

1. **ClassCastException** ✅ 해결
    - PriorityTask 래핑으로 Comparable 보장
    - SecurityManager 제거로 Java 17+ 호환

2. **404 에러** ✅ 해결
    - Hybrid Server에 실제 서블릿 등록
    - Threaded Server와 동일한 엔드포인트 제공

이제 3개 서버 모두 정상적으로 벤치마크 테스트가 가능합니다! 🎉