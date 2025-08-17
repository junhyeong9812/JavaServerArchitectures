# EventLoop SelectionKey Attachment 문제 해결

## 🚨 문제 상황

EventLoopServer 실행 시 HTTP 요청 처리 중 `ClassCastException` 발생:

```
ERROR s.e.EventLoop - Error processing I/O event for channel: /127.0.0.1:53781 
- Error: class server.eventloop.SelectorManager$ChannelContext cannot be cast to 
class server.eventloop.ClientSocketEventHandler
```

### 발생 환경
- **서버**: EventLoopServer (Single Thread + NIO Selector)
- **에러 발생 시점**: HTTP 요청 처리 중 `EventLoop.handleRead()` 메서드
- **빈도**: 모든 HTTP 요청에서 발생

## 🔍 원인 분석

### 문제의 핵심
`SelectionKey`의 attachment 객체 타입 불일치로 인한 캐스팅 에러

### 코드 분석

**1. SelectorManager에서 attachment 설정:**
```java
// SelectorManager.registerClientSocket()
ChannelContext context = new ChannelContext(channelId, clientChannel, handler);
SelectionKey key = clientChannel.register(selector, SelectionKey.OP_READ);
key.attach(context); // ← ChannelContext 객체를 attachment로 설정
```

**2. EventLoop에서 attachment 사용:**
```java
// EventLoop.handleRead()
ClientSocketEventHandler handler = (ClientSocketEventHandler) key.attachment(); 
// ← ChannelContext를 ClientSocketEventHandler로 캐스팅 시도 → ClassCastException!
```

### 아키텍처 설계 문제
- **SelectorManager**: 채널 관리와 통계를 위해 `ChannelContext` 사용
- **EventLoop**: I/O 이벤트 처리를 위해 `ClientSocketEventHandler` 기대
- **인터페이스 불일치**: 두 컴포넌트 간 attachment 사용 방식이 다름

## 💡 해결책 검토

### 옵션 1: EventLoop에서 타입 체크 후 처리
```java
// EventLoop.handleRead() 수정
Object attachment = key.attachment();
ClientSocketEventHandler handler;

if (attachment instanceof ClientSocketEventHandler) {
    handler = (ClientSocketEventHandler) attachment;
} else if (attachment instanceof SelectorManager.ChannelContext) {
    SelectorManager.ChannelContext context = (SelectorManager.ChannelContext) attachment;
    handler = context.getHandler();
}
```

**장점**: 기존 SelectorManager 구조 유지  
**단점**: EventLoop에 추가 로직 필요, 런타임 타입 체크 오버헤드

### 옵션 2: SelectorManager에서 handler 직접 attachment로 설정
```java
// SelectorManager.registerClientSocket() 수정
ChannelContext context = new ChannelContext(channelId, clientChannel, handler);
channelContexts.put(clientChannel, context); // Map에서 별도 관리

SelectionKey key = clientChannel.register(selector, SelectionKey.OP_READ);
key.attach(handler); // ← handler 직접 설정
```

**장점**: EventLoop 코드 단순화, 명확한 인터페이스  
**단점**: SelectorManager 구조 약간 변경

## ✅ 선택한 해결책

**옵션 2: SelectorManager에서 handler 직접 attachment 설정**

### 이유
1. **단순성**: EventLoop의 핵심 로직을 단순하게 유지
2. **성능**: 런타임 타입 체크 없이 직접 캐스팅 가능
3. **명확성**: SelectionKey attachment의 역할이 명확해짐
4. **확장성**: 향후 다른 이벤트 핸들러 추가 시에도 일관된 패턴

### 구현 변경사항

**SelectorManager.registerClientSocket() 수정:**
```java
public void registerClientSocket(SocketChannel clientChannel,
                                 ClientSocketEventHandler handler) {
    try {
        clientChannel.configureBlocking(false);

        // ChannelContext는 Map에서 별도 관리 (통계, 타임아웃 등)
        long channelId = channelIdGenerator.incrementAndGet();
        ChannelContext context = new ChannelContext(channelId, clientChannel, handler);
        channelContexts.put(clientChannel, context);

        // SelectionKey attachment는 handler 직접 설정
        SelectionKey key = clientChannel.register(selector, SelectionKey.OP_READ);
        key.attach(handler); // ← 핵심 변경사항

        totalConnections++;
        activeConnections++;
    } catch (IOException e) {
        logger.error("Failed to register client socket", e);
        closeChannel(clientChannel);
    }
}
```

**EventLoop.handleRead() - 변경 없음:**
```java
private void handleRead(SelectionKey key) throws IOException {
    SocketChannel channel = (SocketChannel) key.channel();
    ClientSocketEventHandler handler = (ClientSocketEventHandler) key.attachment(); // ← 이제 정상 작동
    
    ByteBuffer buffer = ByteBuffer.allocate(8192);
    int bytesRead = channel.read(buffer);
    
    if (bytesRead > 0) {
        buffer.flip();
        handler.onRead(this, channel, buffer);
    } else if (bytesRead == -1) {
        handler.onDisconnect(this, channel);
        closeKey(key);
    }
}
```

## 🎯 결과

### 수정 전
```
ERROR s.e.EventLoop - Error processing I/O event for channel: /127.0.0.1:53781 
- Error: class server.eventloop.SelectorManager$ChannelContext cannot be cast to 
class server.eventloop.ClientSocketEventHandler
```

### 수정 후
```
[2025-08-16 20:43:16.083] INFO  s.e.EventLoopServer  - ✅ EventLoop Server started successfully!
[2025-08-16 20:43:16.083] INFO  s.e.EventLoopServer  -    Server running at: http://localhost:8082
[2025-08-16 20:43:16.083] INFO  s.e.EventLoopServer  -    Architecture: Single Thread + NIO Selector
[2025-08-16 20:43:16.083] INFO  s.e.EventLoopServer  -    Expected concurrent connections: 10,000+

[EventLoop] GET /hello -> 200 OK (36.97ms)
[EventLoop] GET /favicon.ico -> 404 Not Found (0.30ms)
```

### 성능 개선사항
- ✅ **ClassCastException 완전 해결**: 모든 HTTP 요청이 정상 처리
- ✅ **응답 시간 개선**: `/hello` 엔드포인트 정상 응답 (36.97ms)
- ✅ **에러 처리 정상화**: 404 에러도 적절히 처리 (0.30ms)
- ✅ **로깅 시스템 안정화**: 더 이상 attachment 관련 에러 없음

### 유지된 기능
- ✅ **통계 정보**: ChannelContext를 통한 연결 통계 계속 수집
- ✅ **타임아웃 관리**: 연결별 타임아웃 처리 기능 유지
- ✅ **채널 관리**: 채널 생성/삭제 추적 기능 정상 작동
- ✅ **메모리 관리**: ChannelContext Map을 통한 적절한 리소스 정리

## 📋 교훈

### 아키텍처 설계 관점
1. **인터페이스 일관성**: 컴포넌트 간 데이터 전달 방식의 일관성 중요
2. **책임 분리**: 각 컴포넌트의 역할과 책임을 명확히 정의
3. **타입 안전성**: 런타임 캐스팅보다 컴파일 타임 타입 안전성 우선

### 이벤트루프 패턴 학습
1. **SelectionKey attachment**: 이벤트 처리에 필요한 최소한의 정보만 저장
2. **별도 저장소 활용**: 상세한 메타데이터는 Map 등 별도 저장소 사용
3. **성능 우선**: 이벤트루프 내부에서는 최대한 단순하고 빠른 처리

### 디버깅 프로세스
1. **로그 시스템 먼저 수정**: 에러 로그가 제대로 출력되어야 문제 파악 가능
2. **스택 트레이스 분석**: ClassCastException으로 정확한 원인 위치 파악
3. **아키텍처 전체 이해**: 단순한 캐스팅 문제가 아닌 설계 문제임을 인식

---

**결론**: EventLoop 아키텍처에서 SelectionKey의 attachment는 이벤트 처리에 직접 필요한 핸들러만 저장하고, 부가적인 메타데이터는 별도 저장소에서 관리하는 것이 올바른 패턴이다.