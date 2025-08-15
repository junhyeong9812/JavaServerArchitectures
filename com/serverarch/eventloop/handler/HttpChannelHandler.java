package com.serverarch.eventloop.handler; // 패키지 선언 - 이벤트 루프 서버의 핸들러 클래스들을 담는 패키지

// === Java NIO 라이브러리 Import ===
import java.io.IOException; // IOException 클래스 - I/O 작업 중 발생하는 예외 처리용
import java.nio.ByteBuffer; // ByteBuffer 클래스 - NIO에서 바이트 데이터 버퍼링을 위한 클래스
import java.nio.channels.SelectionKey; // SelectionKey 클래스 - NIO Selector에서 이벤트 관리를 위한 키 객체
import java.nio.channels.SocketChannel; // SocketChannel 클래스 - NIO 기반 논블로킹 소켓 채널
import java.nio.charset.StandardCharsets; // StandardCharsets 클래스 - 표준 문자 인코딩 상수 (UTF-8 등)

// === 동시성 처리 Import ===
import java.util.concurrent.CompletableFuture; // CompletableFuture 클래스 - 비동기 작업 결과 처리를 위한 Future 구현체
import java.util.concurrent.ExecutorService; // ExecutorService 인터페이스 - 스레드풀 관리를 위한 인터페이스
import java.util.concurrent.Executors; // Executors 클래스 - 스레드풀 생성을 위한 팩토리 클래스
import java.util.concurrent.TimeUnit; // TimeUnit enum - 시간 단위 표현

// === 컬렉션과 유틸리티 Import ===
import java.util.HashMap; // HashMap 클래스 - 키-값 쌍을 저장하는 해시 맵
import java.util.Map; // Map 인터페이스 - 키-값 쌍 컬렉션의 상위 인터페이스

// === 로깅 Import ===
import java.util.logging.Level; // Level 클래스 - 로깅 레벨 관리 (INFO, WARNING, SEVERE 등)
import java.util.logging.Logger; // Logger 클래스 - 로깅 기능 제공을 위한 클래스

// === 공통 모듈 Import ===
import com.serverarch.common.http.HttpStatus; // HttpStatus enum - HTTP 상태 코드 관리용 (200, 404, 500 등)
import com.serverarch.common.http.HttpHeaders; // HttpHeaders 클래스 - HTTP 헤더 관리용
import com.serverarch.eventloop.core.EventLoop;
import com.serverarch.eventloop.core.EventLoopServer;

// === HTTP 처리 Import ===
import com.serverarch.eventloop.http.HttpRequest; // EventLoop용 HttpRequest 인터페이스
import com.serverarch.eventloop.http.HttpResponse; // EventLoop용 HttpResponse 인터페이스
import com.serverarch.eventloop.http.SimpleHttpRequest; // EventLoop용 HttpRequest 구현체
import com.serverarch.eventloop.routing.Router; // EventLoop용 Router 인터페이스

/**
 * EventLoop 기반 HTTP 채널 핸들러
 *
 * 이 클래스는 NIO 기반 이벤트 루프 서버에서 HTTP 프로토콜을 처리하는 핵심 핸들러입니다.
 * EventLoop.ChannelHandler 인터페이스를 구현하여 이벤트 루프와 통합됩니다.
 *
 * 주요 기능:
 * 1. HTTP 요청 파싱 - 클라이언트로부터 받은 원시 바이트 데이터를 HTTP 요청 객체로 변환
 * 2. 라우터 연동 - 파싱된 요청을 적절한 핸들러로 라우팅
 * 3. HTTP 응답 생성 - 처리 결과를 HTTP 프로토콜 형식으로 직렬화
 * 4. 논블로킹 I/O - 이벤트 루프의 단일 스레드에서 블로킹 없이 처리
 * 5. Keep-Alive 지원 - HTTP/1.1 연결 재사용 기능
 *
 * 아키텍처 특징:
 * - 완전 논블로킹: 모든 I/O 작업이 논블로킹으로 처리됨
 * - 이벤트 기반: READ/WRITE 이벤트에 반응하여 처리
 * - 상태 관리: 각 연결별로 ChannelContext로 상태 추적
 * - 메모리 효율: 버퍼 재사용과 적절한 정리로 메모리 최적화
 */
public class HttpChannelHandler implements EventLoop.ChannelHandler { // public 클래스 선언 - EventLoop.ChannelHandler 인터페이스 구현

    // ========== 상수 정의 ==========

    /**
     * HTTP 프로토콜 라인 구분자
     * HTTP/1.1 RFC 2616에서 정의된 표준 CRLF(Carriage Return + Line Feed) 시퀀스
     * 헤더와 바디를 구분하고, 각 헤더 라인을 구분하는데 사용
     */
    private static final String HTTP_LINE_SEPARATOR = "\r\n"; // static final String - 클래스 레벨 상수, 모든 인스턴스가 공유

    /**
     * HTTP 헤더와 바디 구분자
     * 빈 줄(\r\n\r\n)로 HTTP 헤더 섹션과 바디 섹션을 구분
     * HTTP 요청/응답의 완료 여부를 판단하는 중요한 구분자
     */
    private static final String HTTP_HEADER_BODY_SEPARATOR = HTTP_LINE_SEPARATOR + HTTP_LINE_SEPARATOR; // 상수 조합 - 두 개의 CRLF를 연결

    /**
     * HTTP 요청 파싱 버퍼 최대 크기 (8KB)
     * 너무 큰 헤더나 악의적인 요청으로부터 서버를 보호하기 위한 제한
     * 일반적인 HTTP 요청 헤더 크기를 고려하여 설정
     */
    private static final int MAX_REQUEST_SIZE = 8192; // 8KB - 일반적인 HTTP 요청 헤더 크기 제한

    /**
     * 스레드풀 종료 대기 시간 (초)
     * 서버 종료 시 작업 중인 스레드들이 정상 종료될 때까지 기다리는 시간
     */
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10; // 10초 - 적절한 종료 대기 시간

    // ========== 인스턴스 필드 ==========

    /**
     * 부모 서버 인스턴스 참조
     * 서버의 메트릭 업데이트와 채널 관리 기능에 접근하기 위해 필요
     * final로 선언하여 핸들러 생성 후 변경 불가능하도록 보장
     */
    private final EventLoopServer server; // final EventLoopServer - 불변 참조, 서버 메트릭 및 채널 관리용

    /**
     * HTTP 요청 라우터 인스턴스
     * URL 패턴 매칭과 핸들러 호출을 담당하는 라우팅 시스템
     * 기존 Traditional 서버의 라우터를 재사용하여 일관성 보장
     */
    private final Router router; // final Router - 불변 참조, URL 기반 요청 라우팅 처리

    /**
     * 로거 인스턴스
     * 클래스별 로깅을 위한 전용 로거
     * 디버깅, 모니터링, 오류 추적에 사용
     */
    private final Logger logger; // final Logger - 클래스별 로깅 인스턴스

    /**
     * 응답 처리 전용 스레드풀
     * CPU 집약적인 비즈니스 로직 처리를 별도 스레드에서 수행
     * 이벤트 루프의 단일 스레드가 블로킹되는 것을 방지
     */
    private final ExecutorService responseExecutor; // final ExecutorService - 응답 처리용 스레드풀

    // ========== 생성자 ==========

    /**
     * HttpChannelHandler 생성자
     * 의존성 주입 방식으로 필요한 컴포넌트들을 초기화
     *
     * @param server EventLoopServer 인스턴스 - 메트릭 업데이트와 채널 관리를 위해 필요
     * @param router Router 인스턴스 - HTTP 요청 라우팅 처리를 위해 필요
     * @throws IllegalArgumentException server나 router가 null인 경우
     */
    public HttpChannelHandler(EventLoopServer server, Router router) { // public 생성자 - 의존성 주입으로 초기화
        // null 체크 - 필수 의존성 검증
        if (server == null) { // 조건문 - server 매개변수 null 체크
            throw new IllegalArgumentException("EventLoopServer는 null일 수 없습니다"); // 예외 던지기 - 필수 의존성 누락 시
        }
        if (router == null) { // 조건문 - router 매개변수 null 체크
            throw new IllegalArgumentException("Router는 null일 수 없습니다"); // 예외 던지기 - 필수 의존성 누락 시
        }

        // 인스턴스 필드 초기화
        this.server = server; // 서버 참조 저장 - 메트릭 업데이트와 채널 관리에 사용
        this.router = router; // 라우터 참조 저장 - HTTP 요청 라우팅에 사용
        this.logger = Logger.getLogger(this.getClass().getName()); // Logger.getLogger() - 클래스명 기반 로거 생성

        // 응답 처리 스레드풀 생성
        // CPU 코어 수만큼 스레드 생성하여 병렬 처리 능력 확보
        this.responseExecutor = Executors.newFixedThreadPool( // Executors.newFixedThreadPool() - 고정 크기 스레드풀 생성
                Runtime.getRuntime().availableProcessors() // Runtime.getRuntime().availableProcessors() - 시스템 CPU 코어 수 조회
        );

        // 초기화 완료 로그
        logger.info(String.format("HttpChannelHandler 초기화 완료 - 스레드풀 크기: %d",
                Runtime.getRuntime().availableProcessors())); // String.format() - 형식화된 로그 메시지 생성
    }

    // ========== EventLoop.ChannelHandler 인터페이스 구현 ==========

    /**
     * 이벤트 루프에서 호출되는 메인 이벤트 처리 메서드
     * SelectionKey의 ready operations를 확인하여 적절한 처리 메서드로 분기
     *
     * @param key SelectionKey - 이벤트가 발생한 채널의 키 객체
     * @throws IOException I/O 처리 중 오류 발생 시
     */
    @Override // 어노테이션 - 인터페이스 메서드 구현임을 명시
    public void handleEvent(SelectionKey key) throws IOException { // EventLoop.ChannelHandler 인터페이스의 추상 메서드 구현
        // SelectionKey 유효성 확인
        if (!key.isValid()) { // key.isValid() - SelectionKey가 유효한지 확인 (채널이 열려있고 등록이 유효한지)
            logger.warning("유효하지 않은 SelectionKey 감지, 무시함"); // logger.warning() - 경고 레벨 로그 출력
            return; // early return - 유효하지 않은 키는 처리하지 않고 즉시 반환
        }

        // 채널과 컨텍스트 추출
        SocketChannel channel = (SocketChannel) key.channel(); // 형변환 - SelectableChannel을 SocketChannel로 캐스팅
        EventLoopServer.ChannelContext context = getChannelContext(key); // 메서드 호출 - SelectionKey에서 연관된 채널 컨텍스트 추출

        // 컨텍스트가 없는 경우 처리
        if (context == null) { // 컨텍스트 null 체크
            logger.warning(String.format("SelectionKey에 ChannelContext가 없음: %s", channel)); // 컨텍스트 없음 경고
            return; // early return - 컨텍스트 없이는 처리 불가
        }

        try {
            // 이벤트 타입별 처리 분기
            if (key.isReadable()) { // key.isReadable() - READ 이벤트 발생 여부 확인 (클라이언트로부터 데이터 수신 가능)
                logger.fine(String.format("READ 이벤트 처리 시작: %s", channel)); // logger.fine() - 상세 레벨 로그, 이벤트 처리 시작 기록
                handleRead(key, channel, context); // handleRead() 메서드 호출 - READ 이벤트 처리
            } else if (key.isWritable()) { // key.isWritable() - WRITE 이벤트 발생 여부 확인 (클라이언트로 데이터 전송 가능)
                logger.fine(String.format("WRITE 이벤트 처리 시작: %s", channel)); // WRITE 이벤트 처리 시작 로그
                handleWrite(key, channel, context); // handleWrite() 메서드 호출 - WRITE 이벤트 처리
            } else { // 기타 이벤트 (CONNECT, ACCEPT 등)
                logger.warning(String.format("처리되지 않은 이벤트 타입: %d, 채널: %s",
                        key.readyOps(), channel)); // key.readyOps() - 준비된 연산 비트마스크 조회
            }

        } catch (IOException e) { // IOException - I/O 처리 중 발생하는 예외
            logger.log(Level.WARNING, String.format("채널 처리 중 I/O 오류: %s", channel), e); // logger.log() - 예외와 함께 로그 출력
            handleChannelError(channel, context, e); // handleChannelError() 메서드 호출 - 채널 오류 상황 처리
        } catch (Exception e) { // Exception - 기타 예상치 못한 예외
            logger.log(Level.SEVERE, String.format("채널 처리 중 예상치 못한 오류: %s", channel), e); // Level.SEVERE - 심각한 오류 레벨
            handleChannelError(channel, context, e); // 예상치 못한 오류도 동일하게 처리
        }
    }

    // ========== 읽기 이벤트 처리 ==========

    /**
     * READ 이벤트 처리 메서드
     * 클라이언트로부터 데이터를 읽어와 HTTP 요청으로 파싱하고 처리
     *
     * @param key SelectionKey - 이벤트가 발생한 키
     * @param channel SocketChannel - 클라이언트 소켓 채널
     * @param context ChannelContext - 채널별 상태 정보
     * @throws IOException I/O 처리 중 오류 발생 시
     */
    private void handleRead(SelectionKey key, SocketChannel channel,
                            EventLoopServer.ChannelContext context) throws IOException { // private 메서드 - 내부에서만 사용하는 READ 이벤트 처리

        // 채널 컨텍스트에서 읽기 버퍼 조회
        ByteBuffer readBuffer = context.getReadBuffer(); // context.getReadBuffer() - 채널별 읽기 전용 바이트 버퍼 조회

        // 소켓에서 데이터 읽기 시도
        int bytesRead = channel.read(readBuffer); // channel.read() - 논블로킹 방식으로 소켓에서 데이터 읽기, 읽은 바이트 수 반환

        // 연결 종료 처리
        if (bytesRead == -1) { // bytesRead가 -1 - 클라이언트가 연결을 정상적으로 종료함을 의미
            logger.fine(String.format("클라이언트 연결 종료 감지: %s", channel)); // 정상적인 연결 종료 로그
            handleChannelClosure(channel, context); // handleChannelClosure() 메서드 호출 - 정상적인 채널 종료 처리
            return; // early return - 더 이상 처리할 데이터가 없으므로 메서드 종료
        }

        // 현재 읽을 데이터가 없는 경우 (논블로킹에서 정상)
        if (bytesRead == 0) { // bytesRead가 0 - 현재 읽을 수 있는 데이터가 없음 (논블로킹 소켓에서 정상적인 상황)
            logger.finest("현재 읽을 데이터 없음, 다음 이벤트 대기"); // logger.finest() - 가장 상세한 레벨의 로그
            return; // early return - 다음 READ 이벤트까지 대기
        }

        // 데이터 수신 로그
        logger.fine(String.format("데이터 수신: %d바이트, 채널: %s", bytesRead, channel)); // 수신된 데이터 크기 로그

        // 요청 크기 제한 확인 (보안 조치)
        if (readBuffer.position() > MAX_REQUEST_SIZE) { // readBuffer.position() - 현재 버퍼에 쓰여진 데이터 크기
            logger.warning(String.format("요청 크기 제한 초과: %d > %d, 채널: %s",
                    readBuffer.position(), MAX_REQUEST_SIZE, channel)); // 크기 제한 초과 경고 로그
            sendErrorResponse(channel, context, HttpStatus.REQUEST_ENTITY_TOO_LARGE,
                    "요청 크기가 너무 큽니다"); // sendErrorResponse() 메서드 호출 - 413 Request Entity Too Large 응답 전송
            return; // early return - 더 이상 처리하지 않고 에러 응답 전송
        }

        // 버퍼를 읽기 모드로 전환
        readBuffer.flip(); // readBuffer.flip() - 쓰기 모드에서 읽기 모드로 전환, position을 0으로 limit을 현재 position으로 설정

        try {
            // HTTP 요청 파싱 시도
            HttpRequest request = parseHttpRequest(readBuffer); // parseHttpRequest() 메서드 호출 - 바이트 버퍼를 HTTP 요청 객체로 파싱

            if (request != null) { // request가 null이 아님 - 완전한 HTTP 요청이 파싱됨
                logger.fine(String.format("HTTP 요청 파싱 완료: %s %s",
                        request.getMethod(), request.getPath())); // 파싱 완료된 요청 메서드와 경로 로그

                // 요청 처리를 별도 메서드로 위임
                handleCompleteRequest(key, channel, context, request); // handleCompleteRequest() 메서드 호출 - 완전한 요청 처리
            } else { // request가 null - 아직 요청이 완전히 수신되지 않음
                logger.fine(String.format("부분 요청 수신, 더 많은 데이터 필요: %s", channel)); // 부분 요청 수신 로그
                // 더 많은 데이터를 위해 대기 - 다음 READ 이벤트에서 계속 처리
            }

        } catch (Exception e) { // Exception - HTTP 요청 파싱 중 발생하는 모든 예외
            logger.log(Level.WARNING, String.format("HTTP 요청 파싱 실패: %s", channel), e); // 파싱 실패 로그
            sendErrorResponse(channel, context, HttpStatus.BAD_REQUEST,
                    "잘못된 HTTP 요청 형식입니다"); // 400 Bad Request 응답 전송
        } finally { // finally 블록 - 예외 발생 여부와 관계없이 실행
            // 읽은 데이터 처리 후 버퍼 정리
            readBuffer.compact(); // readBuffer.compact() - 처리되지 않은 데이터를 버퍼 앞쪽으로 이동, 다음 읽기 준비
        }
    }

    // ========== 쓰기 이벤트 처리 ==========

    /**
     * WRITE 이벤트 처리 메서드
     * 이전에 완전히 전송되지 못한 응답 데이터의 나머지 부분을 전송
     *
     * @param key SelectionKey - 이벤트가 발생한 키
     * @param channel SocketChannel - 클라이언트 소켓 채널
     * @param context ChannelContext - 채널별 상태 정보
     * @throws IOException I/O 처리 중 오류 발생 시
     */
    private void handleWrite(SelectionKey key, SocketChannel channel,
                             EventLoopServer.ChannelContext context) throws IOException { // private 메서드 - WRITE 이벤트 처리

        // 채널 컨텍스트에서 쓰기 버퍼 조회
        ByteBuffer writeBuffer = context.getWriteBuffer(); // context.getWriteBuffer() - 채널별 쓰기 전용 바이트 버퍼 조회

        // 전송할 데이터가 있는지 확인
        if (!writeBuffer.hasRemaining()) { // writeBuffer.hasRemaining() - 버퍼에 읽을 수 있는 데이터가 남아있는지 확인
            logger.fine(String.format("WRITE 이벤트 발생했지만 전송할 데이터 없음: %s", channel)); // 전송할 데이터 없음 로그

            // WRITE 관심사 제거 - 더 이상 쓰기 이벤트를 받을 필요가 없음
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE); // 비트 AND NOT 연산으로 WRITE 플래그 제거
            return; // early return - 전송할 데이터가 없으므로 메서드 종료
        }

        // 남은 데이터 전송 시도
        int bytesWritten = channel.write(writeBuffer); // channel.write() - 논블로킹 방식으로 소켓에 데이터 쓰기, 실제 전송된 바이트 수 반환

        logger.fine(String.format("데이터 전송: %d바이트, 남은 데이터: %d바이트, 채널: %s",
                bytesWritten, writeBuffer.remaining(), channel)); // 전송 진행 상황 로그

        // 모든 데이터 전송 완료 확인
        if (!writeBuffer.hasRemaining()) { // 모든 데이터가 전송되었는지 확인
            // WRITE 관심사 제거 - 전송 완료되었으므로 더 이상 WRITE 이벤트 불필요
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE); // WRITE 플래그 제거

            logger.fine(String.format("응답 전송 완료: %s", channel)); // 응답 전송 완료 로그

            // 서버 메트릭 업데이트
            server.recordResponse(); // server.recordResponse() - 서버의 총 응답 수 카운터 증가

            // 연결 완료 후처리 (Keep-Alive 또는 연결 종료 결정)
            handleConnectionCompletion(channel, context); // handleConnectionCompletion() 메서드 호출 - 연결 유지/종료 결정
        }
        // 아직 남은 데이터가 있으면 다음 WRITE 이벤트에서 계속 전송
    }

    // ========== HTTP 요청 파싱 ==========

    /**
     * ByteBuffer에서 HTTP 요청을 파싱하는 메서드
     * HTTP/1.1 프로토콜 형식에 따라 요청 라인, 헤더, 바디를 파싱
     *
     * @param buffer ByteBuffer - 클라이언트로부터 수신된 원시 데이터
     * @return HttpRequest - 파싱된 HTTP 요청 객체, 불완전한 요청인 경우 null
     */
    private HttpRequest parseHttpRequest(ByteBuffer buffer) { // private 메서드 - HTTP 요청 파싱 처리
        // 버퍼에 데이터가 있는지 확인
        if (!buffer.hasRemaining()) { // buffer.hasRemaining() - 읽을 수 있는 데이터가 있는지 확인
            logger.finest("파싱할 데이터가 없음"); // 파싱할 데이터 없음 로그
            return null; // null 반환 - 파싱할 데이터가 없음
        }

        // 버퍼 데이터를 문자열로 변환
        byte[] bytes = new byte[buffer.remaining()]; // new byte[] - 버퍼의 남은 데이터 크기만큼 바이트 배열 생성
        buffer.get(bytes); // buffer.get() - 버퍼의 데이터를 바이트 배열로 복사
        String requestString = new String(bytes, StandardCharsets.UTF_8); // new String() - 바이트 배열을 UTF-8 문자열로 변환

        // HTTP 요청 완료 여부 확인
        // HTTP 요청은 헤더와 바디 사이에 빈 줄(\r\n\r\n)이 있어야 완료된 것으로 간주
        if (!requestString.contains(HTTP_HEADER_BODY_SEPARATOR)) { // String.contains() - 헤더-바디 구분자 존재 여부 확인
            logger.finest("HTTP 요청 미완료, 헤더-바디 구분자 없음"); // 미완료 요청 로그
            return null; // null 반환 - 아직 완전한 요청이 수신되지 않음
        }

        // 실제 HTTP 파싱 수행
        try {
            return parseHttpRequestString(requestString); // parseHttpRequestString() 메서드 호출 - 문자열을 실제 HTTP 요청 객체로 파싱
        } catch (Exception e) { // Exception - 파싱 중 발생하는 모든 예외
            logger.log(Level.WARNING, "HTTP 요청 문자열 파싱 실패", e); // 파싱 실패 로그
            throw new RuntimeException("HTTP 요청 파싱 오류", e); // RuntimeException 던지기 - 파싱 오류를 상위로 전파
        }
    }

    /**
     * HTTP 요청 문자열을 실제 HttpRequest 객체로 파싱
     * EventLoop용 SimpleHttpRequest로 생성하여 byte[] 바디 지원
     *
     * @param requestString String - HTTP 요청 전체 문자열
     * @return HttpRequest - 파싱된 EventLoop용 HTTP 요청 객체
     * @throws IllegalArgumentException 잘못된 HTTP 형식인 경우
     */
    private HttpRequest parseHttpRequestString(String requestString) { // private 메서드 - 문자열 기반 HTTP 요청 파싱
        // 헤더와 바디 분리
        String[] parts = requestString.split(HTTP_HEADER_BODY_SEPARATOR, 2); // String.split() - 헤더-바디 구분자로 분리, 최대 2개 부분으로 제한
        String headerPart = parts[0]; // 배열 인덱스 접근 - 첫 번째 부분은 헤더
        String bodyPart = parts.length > 1 ? parts[1] : ""; // 삼항 연산자 - 두 번째 부분이 있으면 바디, 없으면 빈 문자열

        // 헤더 부분을 라인별로 분리
        String[] headerLines = headerPart.split(HTTP_LINE_SEPARATOR); // HTTP_LINE_SEPARATOR로 각 헤더 라인 분리

        if (headerLines.length == 0) { // 배열 길이 확인 - 헤더 라인이 없는 경우
            throw new IllegalArgumentException("HTTP 요청 라인이 없습니다"); // 예외 던지기 - 필수 요청 라인 누락
        }

        // 요청 라인 파싱 (예: "GET /path HTTP/1.1")
        String requestLine = headerLines[0]; // 배열 첫 번째 요소 - HTTP 요청의 첫 번째 라인
        String[] requestParts = requestLine.split(" "); // 공백으로 분리 - HTTP 메서드, URL, 프로토콜 버전

        if (requestParts.length != 3) { // 배열 길이 확인 - HTTP 요청 라인은 반드시 3개 부분이어야 함
            throw new IllegalArgumentException("잘못된 HTTP 요청 라인 형식: " + requestLine); // 잘못된 형식 예외
        }

        String method = requestParts[0]; // HTTP 메서드 (GET, POST, PUT, DELETE 등)
        String path = requestParts[1]; // 요청 경로 (URL 경로 부분)
        String httpVersion = requestParts[2]; // HTTP 버전 (HTTP/1.1 등)

        // 헤더 파싱 - 요청 라인 이후의 모든 라인들
        HttpHeaders headers = new HttpHeaders(); // new HttpHeaders() - EventLoop용 HttpHeaders 객체 생성
        for (int i = 1; i < headerLines.length; i++) { // for 반복문 - 요청 라인 다음부터 모든 헤더 라인 순회
            String headerLine = headerLines[i]; // 현재 헤더 라인

            // 빈 라인 건너뛰기
            if (headerLine.trim().isEmpty()) { // String.trim().isEmpty() - 공백 제거 후 빈 문자열 확인
                continue; // continue 문 - 현재 반복을 건너뛰고 다음 반복으로
            }

            // 헤더 이름과 값 분리 (예: "Content-Type: application/json")
            int colonIndex = headerLine.indexOf(':'); // String.indexOf() - 콜론 문자의 위치 찾기
            if (colonIndex > 0) { // 콜론이 존재하고 첫 번째 문자가 아닌 경우
                String headerName = headerLine.substring(0, colonIndex).trim(); // String.substring() - 콜론 이전 부분을 헤더 이름으로
                String headerValue = headerLine.substring(colonIndex + 1).trim(); // 콜론 이후 부분을 헤더 값으로
                headers.set(headerName, headerValue); // headers.set() - 헤더 이름과 값을 HttpHeaders 객체에 저장
            }
        }

        // EventLoop용 SimpleHttpRequest 생성 - byte[] 바디 지원
        byte[] bodyBytes = bodyPart.getBytes(StandardCharsets.UTF_8); // String.getBytes() - 문자열을 UTF-8 바이트 배열로 변환

        // SimpleHttpRequest 생성자 호출 - EventLoop용 구현체 사용
        return new SimpleHttpRequest(method, path, headers, bodyBytes);
        // new SimpleHttpRequest() - EventLoop용 HttpRequest 구현체 생성
        // method - HTTP 메서드
        // path - 요청 경로
        // headers - HttpHeaders 객체
        // bodyBytes - 바디를 byte[] 형태로 전달
    }

    // ========== 완전한 요청 처리 ==========

    /**
     * 완전히 파싱된 HTTP 요청을 처리하는 메서드
     * 라우터를 통해 적절한 핸들러로 요청을 전달하고 비동기로 응답 처리
     *
     * @param key SelectionKey - 이벤트 키
     * @param channel SocketChannel - 클라이언트 채널
     * @param context ChannelContext - 채널 컨텍스트
     * @param request HttpRequest - 파싱된 HTTP 요청
     */
    private void handleCompleteRequest(SelectionKey key, SocketChannel channel,
                                       EventLoopServer.ChannelContext context, HttpRequest request) { // private 메서드 - 완전한 요청 처리

        // null 체크 - 방어적 프로그래밍
        if (request == null) { // 요청 객체 null 체크
            logger.warning(String.format("완료된 요청이지만 요청 객체가 null: %s", channel)); // null 요청 경고 로그
            sendErrorResponse(channel, context, HttpStatus.INTERNAL_SERVER_ERROR,
                    "내부 서버 오류가 발생했습니다"); // 500 Internal Server Error 응답 전송
            return; // early return - 처리할 요청이 없으므로 종료
        }

        // 요청 처리 시작 로그
        logger.info(String.format("HTTP 요청 처리 시작: %s %s from %s",
                request.getMethod(), request.getPath(), getRemoteAddress(channel))); // 요청 메서드, 경로, 채널 정보 로그

        // 서버 메트릭 업데이트
        server.recordRequest(); // server.recordRequest() - 서버의 총 요청 수 카운터 증가

        // 현재 요청을 컨텍스트에 저장
        context.setCurrentRequest(request); // context.setCurrentRequest() - 현재 처리 중인 요청을 컨텍스트에 저장

        try {
            // 라우터를 통한 비동기 요청 처리
            CompletableFuture<HttpResponse> responseFuture = router.route(request); // router.route() - 요청을 적절한 핸들러로 라우팅, 비동기 응답 Future 반환

            // 응답 처리를 별도 스레드에서 비동기로 수행
            responseFuture
                    .thenAcceptAsync(response -> { // CompletableFuture.thenAcceptAsync() - 응답 성공 시 비동기 처리
                        try {
                            handleSuccessResponse(channel, context, response); // handleSuccessResponse() - 성공적인 응답 처리
                        } catch (Exception e) { // 응답 처리 중 예외
                            logger.log(Level.SEVERE, "응답 처리 중 오류", e); // 응답 처리 오류 로그
                            handleChannelError(channel, context, e); // 채널 오류 처리
                        }
                    }, responseExecutor) // responseExecutor - 응답 처리 전용 스레드풀에서 실행
                    .exceptionally(throwable -> { // CompletableFuture.exceptionally() - 예외 발생 시 처리
                        logger.log(Level.WARNING, String.format("요청 처리 중 오류 발생: %s %s",
                                request.getMethod(), request.getPath()), throwable); // 요청 처리 오류 로그
                        sendErrorResponse(channel, context, HttpStatus.INTERNAL_SERVER_ERROR,
                                "요청 처리 중 오류가 발생했습니다: " + throwable.getMessage()); // 500 오류 응답 전송
                        return null; // null 반환 - Void 타입 반환을 위한 더미 값
                    });

        } catch (Exception e) { // 라우팅 중 동기 예외
            logger.log(Level.SEVERE, String.format("라우팅 중 예외 발생: %s %s",
                    request.getMethod(), request.getPath()), e); // 라우팅 예외 로그
            sendErrorResponse(channel, context, HttpStatus.INTERNAL_SERVER_ERROR,
                    "라우팅 처리 실패"); // 500 오류 응답 전송
        }
    }

    // ========== 응답 처리 ==========

    /**
     * 성공적인 응답을 클라이언트로 전송하는 메서드
     * HTTP 응답을 직렬화하고 소켓을 통해 전송
     *
     * @param channel SocketChannel - 클라이언트 채널
     * @param context ChannelContext - 채널 컨텍스트
     * @param response HttpResponse - 전송할 HTTP 응답
     */
    private void handleSuccessResponse(SocketChannel channel, EventLoopServer.ChannelContext context,
                                       HttpResponse response) { // private 메서드 - 성공 응답 처리
        try {
            // HTTP 응답을 바이트 배열로 직렬화
            byte[] responseBytes = serializeHttpResponse(response); // serializeHttpResponse() - HttpResponse를 HTTP 프로토콜 바이트로 변환

            // 응답 크기 로그
            logger.fine(String.format("응답 직렬화 완료: %d바이트, 상태: %s",
                    responseBytes.length, response.getStatus())); // 직렬화 완료 로그

            // 쓰기 버퍼 준비
            ByteBuffer writeBuffer = context.getWriteBuffer(); // 채널의 쓰기 버퍼 조회
            writeBuffer.clear(); // 버퍼 초기화 - position을 0으로, limit을 capacity로 설정

            // 버퍼 용량 확인 및 필요시 확장
            if (responseBytes.length > writeBuffer.capacity()) { // 응답 크기가 버퍼 용량 초과 확인
                logger.info(String.format("응답 크기(%d)가 버퍼 용량(%d)을 초과, 새 버퍼 할당",
                        responseBytes.length, writeBuffer.capacity())); // 버퍼 용량 초과 로그

                // 새로운 큰 버퍼 할당 - 여유 공간 포함
                writeBuffer = ByteBuffer.allocate(responseBytes.length + 1024); // ByteBuffer.allocate() - 응답 크기 + 1KB 여유공간으로 새 버퍼 생성
                context.setWriteBuffer(writeBuffer); // context에 새 버퍼 설정 (ChannelContext에 setWriteBuffer 메서드 필요)
            }

            // 응답 데이터를 버퍼에 저장
            writeBuffer.put(responseBytes); // writeBuffer.put() - 응답 바이트를 버퍼에 저장
            writeBuffer.flip(); // 버퍼를 쓰기 모드에서 읽기 모드로 전환

            // 즉시 전송 시도
            int bytesWritten = channel.write(writeBuffer); // channel.write() - 논블로킹으로 소켓에 데이터 전송

            logger.fine(String.format("즉시 전송 시도: %d/%d바이트 전송됨",
                    bytesWritten, responseBytes.length)); // 즉시 전송 결과 로그

            // 전송 완료 여부 확인
            if (writeBuffer.hasRemaining()) { // 아직 전송되지 않은 데이터가 있는 경우
                // WRITE 이벤트에 관심 등록 - 나머지 데이터 전송을 위해
                SelectionKey key = context.getSelectionKey(); // 채널의 SelectionKey 조회
                if (key != null && key.isValid()) { // 키가 유효한지 확인
                    key.interestOps(key.interestOps() | SelectionKey.OP_WRITE); // 기존 관심사에 WRITE 추가
                    logger.fine(String.format("부분 전송, WRITE 이벤트 등록: %d바이트 남음",
                            writeBuffer.remaining())); // 부분 전송 상태 로그
                }
            } else { // 모든 데이터가 즉시 전송 완료
                logger.fine(String.format("응답 즉시 전송 완료: %s", channel)); // 즉시 전송 완료 로그
                server.recordResponse(); // 응답 카운터 증가
                handleConnectionCompletion(channel, context); // 연결 완료 처리
            }

        } catch (IOException e) { // I/O 예외 처리
            logger.log(Level.WARNING, String.format("응답 전송 중 I/O 오류: %s", channel), e); // I/O 오류 로그
            handleChannelError(channel, context, e); // 채널 오류 처리
        } catch (Exception e) { // 기타 예외 처리
            logger.log(Level.SEVERE, String.format("응답 처리 중 예상치 못한 오류: %s", channel), e); // 예상치 못한 오류 로그
            handleChannelError(channel, context, e); // 채널 오류 처리
        }
    }

    // ========== HTTP 응답 직렬화 ==========

    /**
     * HttpResponse 객체를 HTTP 프로토콜 형식의 바이트 배열로 직렬화
     * HTTP/1.1 표준에 따라 상태 라인, 헤더, 바디 순으로 구성
     *
     * @param response HttpResponse - 직렬화할 응답 객체
     * @return byte[] - HTTP 프로토콜 형식의 바이트 배열
     */
    private byte[] serializeHttpResponse(HttpResponse response) { // private 메서드 - HTTP 응답 직렬화
        try {
            StringBuilder responseBuilder = new StringBuilder(); // StringBuilder - 효율적인 문자열 조합을 위한 클래스

            // 1. 상태 라인 구성 (예: "HTTP/1.1 200 OK")
            responseBuilder.append("HTTP/1.1 ") // HTTP 프로토콜 버전
                    .append(response.getStatusCode()) // HTTP 상태 코드 (200, 404, 500 등)
                    .append(" ")
                    .append(response.getStatusMessage()) // 상태 메시지 (OK, Not Found, Internal Server Error 등)
                    .append(HTTP_LINE_SEPARATOR); // CRLF로 라인 종료

            // 2. 응답 헤더 구성
            com.serverarch.common.http.HttpHeaders headers = response.getHeaders(); // 응답 헤더 조회
            if (headers != null) { // 헤더가 존재하는 경우
                for (String headerName : headers.getNames()) { // 모든 헤더 이름 순회
                    for (String headerValue : headers.get(headerName)) { // 각 헤더의 모든 값 순회 (멀티 값 헤더 지원)
                        responseBuilder.append(headerName) // 헤더 이름
                                .append(": ") // 콜론과 공백
                                .append(headerValue) // 헤더 값
                                .append(HTTP_LINE_SEPARATOR); // CRLF로 헤더 라인 종료
                    }
                }
            }

            // 3. 응답 바디 처리
            byte[] bodyBytes = response.getBody(); // 응답 바디를 바이트 배열로 조회
            if (bodyBytes != null && bodyBytes.length > 0) { // 바디가 존재하는 경우

                // Content-Length 헤더 자동 추가 (누락된 경우)
                if (headers == null || headers.getFirst("Content-Length") == null) { // Content-Length 헤더가 없는 경우
                    responseBuilder.append("Content-Length: ") // Content-Length 헤더 추가
                            .append(bodyBytes.length) // 바디 바이트 크기
                            .append(HTTP_LINE_SEPARATOR);
                }

                // Content-Type 헤더 자동 추가 (누락된 경우)
                if (headers == null || headers.getFirst("Content-Type") == null) { // Content-Type 헤더가 없는 경우
                    responseBuilder.append("Content-Type: text/plain; charset=UTF-8") // 기본 Content-Type 설정
                            .append(HTTP_LINE_SEPARATOR);
                }
            } else { // 바디가 없는 경우
                // Content-Length: 0 명시적 설정
                responseBuilder.append("Content-Length: 0") // 명시적으로 Content-Length 0 설정
                        .append(HTTP_LINE_SEPARATOR);
            }

            // 4. 헤더-바디 구분자 추가
            responseBuilder.append(HTTP_LINE_SEPARATOR); // 빈 줄로 헤더와 바디 구분

            // 5. 헤더 부분을 바이트 배열로 변환
            byte[] headerBytes = responseBuilder.toString().getBytes(StandardCharsets.UTF_8); // 헤더를 UTF-8 바이트로 변환

            // 6. 전체 응답 조합 (헤더 + 바디)
            if (bodyBytes != null && bodyBytes.length > 0) { // 바디가 있는 경우
                byte[] fullResponse = new byte[headerBytes.length + bodyBytes.length]; // 전체 크기의 바이트 배열 생성
                System.arraycopy(headerBytes, 0, fullResponse, 0, headerBytes.length); // 헤더 복사
                System.arraycopy(bodyBytes, 0, fullResponse, headerBytes.length, bodyBytes.length); // 바디 복사
                return fullResponse; // 완성된 응답 반환
            } else { // 바디가 없는 경우
                return headerBytes; // 헤더만 반환
            }

        } catch (Exception e) { // 직렬화 중 예외 발생
            logger.log(Level.SEVERE, "HTTP 응답 직렬화 실패", e); // 직렬화 실패 로그

            // 기본 500 에러 응답 생성
            String errorResponse = "HTTP/1.1 500 Internal Server Error\r\n" +
                    "Content-Type: text/plain; charset=UTF-8\r\n" +
                    "Content-Length: 21\r\n" +
                    "\r\n" +
                    "Internal Server Error"; // 하드코딩된 500 에러 응답
            return errorResponse.getBytes(StandardCharsets.UTF_8); // UTF-8 바이트로 변환하여 반환
        }
    }

    // ========== 에러 응답 처리 ==========

    /**
     * 클라이언트에게 HTTP 에러 응답을 전송하는 메서드
     * 에러 상황에서 적절한 HTTP 상태 코드와 메시지를 전송
     *
     * @param channel SocketChannel - 클라이언트 채널
     * @param context ChannelContext - 채널 컨텍스트
     * @param status HttpStatus - HTTP 에러 상태 코드
     * @param message String - 에러 메시지
     */
    private void sendErrorResponse(SocketChannel channel, EventLoopServer.ChannelContext context,
                                   HttpStatus status, String message) { // private 메서드 - 에러 응답 전송
        try {
            // HttpResponse 에러 객체 생성 - 상태 코드에 따른 적절한 팩토리 메서드 사용
            HttpResponse errorResponse;
            switch (status.getCode()) { // switch 문 - 상태 코드별 분기 처리
                case 400: // Bad Request
                    errorResponse = HttpResponse.badRequest(message); // 400 에러 응답 생성
                    break;
                case 404: // Not Found
                    errorResponse = HttpResponse.notFound(message); // 404 에러 응답 생성
                    break;
                case 413: // Request Entity Too Large
                    errorResponse = HttpResponse.requestEntityTooLarge(message); // HttpResponse.requestEntityTooLarge() - 413 에러 응답 생성
                    break;
                case 500: // Internal Server Error
                default: // 기타 모든 에러
                    errorResponse = HttpResponse.serverError(message); // 500 에러 응답 생성
                    break;
            }

            // 에러 응답 전송
            handleSuccessResponse(channel, context, errorResponse); // 에러 응답도 일반 응답과 동일한 방식으로 처리

            // 에러 메트릭 업데이트
            server.recordError(); // server.recordError() - 서버의 총 에러 수 카운터 증가

            // 에러 로그 출력
            logger.info(String.format("에러 응답 전송: %d %s - %s to %s",
                    status.getCode(), status.getReasonPhrase(), message, getRemoteAddress(channel))); // 에러 응답 전송 로그

        } catch (Exception e) { // 에러 응답 전송 중 추가 예외 발생
            logger.log(Level.SEVERE, String.format("에러 응답 전송 실패: %s", channel), e); // 에러 응답 전송 실패 로그
            // 에러 응답도 실패한 경우 채널 강제 종료
            handleChannelError(channel, context, e); // 채널 강제 종료 처리
        }
    }

    // ========== 연결 완료 처리 ==========

    /**
     * HTTP 응답 전송 완료 후 연결 유지 또는 종료를 결정하는 메서드
     * HTTP/1.1 Keep-Alive 기능을 지원하여 연결 재사용 가능
     *
     * @param channel SocketChannel - 클라이언트 채널
     * @param context ChannelContext - 채널 컨텍스트
     */
    private void handleConnectionCompletion(SocketChannel channel, EventLoopServer.ChannelContext context) { // private 메서드 - 연결 완료 처리
        try {
            // HTTP/1.1 Keep-Alive 확인
            HttpRequest request = context.getCurrentRequest(); // 현재 처리된 요청 조회
            boolean keepAlive = false; // Keep-Alive 플래그 초기화

            if (request != null) { // 요청이 존재하는 경우
                com.serverarch.common.http.HttpHeaders headers = request.getHeaders(); // 요청 헤더 조회
                String connection = headers.getFirst("Connection"); // Connection 헤더 값 조회

                // Connection 헤더 값에 따른 Keep-Alive 결정
                keepAlive = "keep-alive".equalsIgnoreCase(connection); // 대소문자 구분 없이 keep-alive 확인

                logger.fine(String.format("Connection 헤더: %s, Keep-Alive: %b", connection, keepAlive)); // Connection 헤더 상태 로그
            }

            if (keepAlive) { // Keep-Alive 연결인 경우
                logger.fine(String.format("Keep-Alive 연결 재사용: %s", channel)); // Keep-Alive 로그

                // 다음 요청을 위해 컨텍스트 초기화
                context.resetBuffers(); // 버퍼와 요청 상태 초기화

                // READ 이벤트만 관심사로 설정 - 다음 요청 대기
                SelectionKey key = context.getSelectionKey(); // SelectionKey 조회
                if (key != null && key.isValid()) { // 키가 유효한 경우
                    key.interestOps(SelectionKey.OP_READ); // READ 이벤트에만 관심 등록
                    logger.fine("다음 요청 대기를 위해 READ 이벤트 등록"); // 다음 요청 대기 로그
                }

            } else { // Keep-Alive가 아닌 경우 연결 종료
                logger.fine(String.format("연결 종료: %s", channel)); // 연결 종료 로그
                handleChannelClosure(channel, context); // 정상적인 연결 종료 처리
            }

        } catch (Exception e) { // 연결 완료 처리 중 예외
            logger.log(Level.WARNING, String.format("연결 완료 처리 중 오류: %s", channel), e); // 연결 완료 처리 오류 로그
            handleChannelError(channel, context, e); // 채널 오류 처리
        }
    }

    // ========== 채널 상태 관리 ==========

    /**
     * SelectionKey에서 ChannelContext를 추출하는 메서드
     * SelectionKey의 attachment로 저장된 컨텍스트를 안전하게 조회
     *
     * @param key SelectionKey - 컨텍스트를 조회할 키
     * @return ChannelContext - 채널별 상태 정보, 없으면 null
     */
    private EventLoopServer.ChannelContext getChannelContext(SelectionKey key) { // private 메서드 - 채널 컨텍스트 조회
        try {
            Object attachment = key.attachment(); // SelectionKey.attachment() - 등록 시 첨부된 객체 조회
            if (attachment instanceof EventLoopServer.ChannelContext) { // instanceof - 타입 확인
                return (EventLoopServer.ChannelContext) attachment; // 형변환하여 반환
            } else { // 컨텍스트가 없거나 잘못된 타입
                logger.warning(String.format("SelectionKey에 유효한 ChannelContext가 없음: %s", key.channel())); // 컨텍스트 없음 경고
                return null; // null 반환
            }
        } catch (Exception e) { // 컨텍스트 조회 중 예외
            logger.log(Level.WARNING, "ChannelContext 조회 중 오류", e); // 조회 오류 로그
            return null; // null 반환
        }
    }

    // ========== 오류 처리 ==========

    /**
     * 채널에서 오류가 발생한 경우의 처리 메서드
     * 리소스 정리와 메트릭 업데이트를 수행
     *
     * @param channel SocketChannel - 오류가 발생한 채널
     * @param context ChannelContext - 채널 컨텍스트
     * @param error Exception - 발생한 오류
     */
    private void handleChannelError(SocketChannel channel, EventLoopServer.ChannelContext context,
                                    Exception error) { // private 메서드 - 채널 오류 처리
        try {
            logger.fine(String.format("채널 오류 처리 시작: %s, 오류: %s",
                    getRemoteAddress(channel), error.getMessage())); // 채널 오류 처리 시작 로그

            // 에러 메트릭 업데이트
            server.recordError(); // 서버 에러 카운터 증가

            // 채널 정리
            cleanupChannel(channel, context); // 채널과 관련 리소스 정리

        } catch (Exception cleanupError) { // 정리 중 추가 오류 발생
            logger.log(Level.WARNING, String.format("채널 오류 처리 중 추가 오류: %s",
                    getRemoteAddress(channel)), cleanupError); // 추가 오류 로그
        }
    }

    /**
     * 클라이언트가 정상적으로 연결을 종료한 경우의 처리 메서드
     * 정상적인 연결 종료에 대한 정리 작업 수행
     *
     * @param channel SocketChannel - 종료할 채널
     * @param context ChannelContext - 채널 컨텍스트
     */
    private void handleChannelClosure(SocketChannel channel, EventLoopServer.ChannelContext context) { // private 메서드 - 정상적인 채널 종료 처리
        try {
            logger.fine(String.format("채널 정상 종료 처리: %s", getRemoteAddress(channel))); // 정상 종료 처리 로그

            // 정상적인 연결 종료는 에러가 아니므로 에러 카운터는 증가시키지 않음
            // 채널 정리만 수행
            cleanupChannel(channel, context); // 채널과 관련 리소스 정리

        } catch (Exception e) { // 정리 중 예외 발생
            logger.log(Level.WARNING, String.format("채널 정상 종료 처리 중 오류: %s",
                    getRemoteAddress(channel)), e); // 정리 중 오류 로그
        }
    }

    /**
     * 채널과 관련된 모든 리소스를 정리하는 메서드
     * 메모리 누수 방지와 서버 상태 일관성 유지를 위한 정리 작업
     *
     * @param channel SocketChannel - 정리할 채널
     * @param context ChannelContext - 채널 컨텍스트
     */
    private void cleanupChannel(SocketChannel channel, EventLoopServer.ChannelContext context) { // private 메서드 - 채널 리소스 정리
        try {
            // 1. 서버의 활성 채널 목록에서 제거
            server.removeActiveChannel(channel); // server.removeActiveChannel() - 서버의 채널 관리 목록에서 제거

            // 2. 연결 해제 메트릭 업데이트
            server.recordDisconnection(); // server.recordDisconnection() - 활성 연결 수 감소

            // 3. 컨텍스트 정리
            if (context != null) { // 컨텍스트가 존재하는 경우
                context.cleanup(); // context.cleanup() - 컨텍스트의 리소스 정리 (버퍼, SelectionKey 등)
            }

            // 4. 채널 닫기
            if (channel != null && channel.isOpen()) { // 채널이 존재하고 열려있는 경우
                channel.close(); // channel.close() - SocketChannel 닫기
            }

            logger.fine(String.format("채널 정리 완료: %s", getRemoteAddress(channel))); // 채널 정리 완료 로그

        } catch (IOException e) { // I/O 예외 (채널 닫기 실패 등)
            logger.log(Level.WARNING, String.format("채널 닫기 실패: %s",
                    getRemoteAddress(channel)), e); // 채널 닫기 실패 로그
        } catch (Exception e) { // 기타 정리 중 예외
            logger.log(Level.WARNING, String.format("채널 정리 중 오류: %s",
                    getRemoteAddress(channel)), e); // 정리 중 기타 오류 로그
        }
    }

    // ========== 핸들러 생명주기 관리 ==========

    /**
     * HttpChannelHandler 종료 메서드
     * 서버 종료 시 핸들러의 모든 리소스를 안전하게 정리
     * 스레드풀 종료와 리소스 해제를 담당
     */
    public void shutdown() { // public 메서드 - 핸들러 종료 처리
        try {
            logger.info("HttpChannelHandler 종료 시작..."); // 종료 시작 로그

            // 응답 처리 스레드풀 우아한 종료
            responseExecutor.shutdown(); // ExecutorService.shutdown() - 새로운 작업 수락 중단, 기존 작업은 완료까지 대기

            // 스레드풀 종료 대기 (최대 10초)
            boolean terminated = responseExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS); // awaitTermination() - 지정 시간동안 종료 대기

            if (!terminated) { // 정상 종료되지 않은 경우
                logger.warning(String.format("응답 처리 스레드풀이 %d초 내에 종료되지 않아 강제 종료합니다",
                        SHUTDOWN_TIMEOUT_SECONDS)); // 강제 종료 경고 로그
                responseExecutor.shutdownNow(); // ExecutorService.shutdownNow() - 실행 중인 작업 중단하고 즉시 종료

                // 추가 대기 시간 (최대 5초)
                if (!responseExecutor.awaitTermination(5, TimeUnit.SECONDS)) { // 강제 종료 후 추가 대기
                    logger.severe("응답 처리 스레드풀 강제 종료도 실패"); // 강제 종료 실패 로그
                }
            }

            logger.info("HttpChannelHandler 종료 완료"); // 종료 완료 로그

        } catch (InterruptedException e) { // 종료 대기 중 인터럽트 발생
            Thread.currentThread().interrupt(); // 현재 스레드의 인터럽트 상태 복원
            responseExecutor.shutdownNow(); // 즉시 강제 종료
            logger.info("HttpChannelHandler 인터럽트로 인한 강제 종료"); // 인터럽트 종료 로그
        } catch (Exception e) { // 기타 종료 중 예외
            logger.log(Level.WARNING, "HttpChannelHandler 종료 중 오류", e); // 종료 중 오류 로그
        }
    }

    /**
     * 핸들러 상태 확인 메서드
     * 스레드풀의 상태를 확인하여 핸들러가 정상 동작 중인지 판단
     *
     * @return boolean - 핸들러가 정상 동작 중이면 true
     */
    public boolean isActive() { // public 메서드 - 핸들러 활성 상태 확인
        return !responseExecutor.isShutdown(); // 스레드풀이 종료되지 않았으면 활성 상태
    }

    // ========== 헬퍼 메서드들 ==========

    /**
     * 채널의 원격 주소를 안전하게 조회하는 메서드
     * 채널이 닫혀있거나 오류가 있는 경우에도 안전하게 처리
     *
     * @param channel SocketChannel - 주소를 조회할 채널
     * @return String - 원격 주소 문자열, 조회 실패 시 "unknown"
     */
    private String getRemoteAddress(SocketChannel channel) { // private 메서드 - 안전한 원격 주소 조회
        try {
            if (channel != null && channel.isOpen()) { // 채널이 존재하고 열려있는 경우
                return channel.getRemoteAddress().toString(); // 원격 주소를 문자열로 반환
            }
        } catch (Exception e) { // 주소 조회 실패
            // 로그는 출력하지 않음 - 너무 빈번할 수 있음
        }
        return "unknown"; // 조회 실패 시 기본값 반환
    }

    /**
     * 요청 처리 시간을 측정하기 위한 헬퍼 메서드
     * 성능 모니터링과 최적화에 활용
     *
     * @param startTime long - 시작 시간 (밀리초)
     * @return long - 경과 시간 (밀리초)
     */
    private long getElapsedTime(long startTime) { // private 메서드 - 경과 시간 계산
        return System.currentTimeMillis() - startTime; // 현재 시간에서 시작 시간을 뺀 경과 시간 반환
    }

    /**
     * 디버깅을 위한 버퍼 상태 로그 출력 메서드
     * 개발 및 디버깅 시에만 사용, 운영에서는 로그 레벨 조정으로 비활성화
     *
     * @param buffer ByteBuffer - 상태를 확인할 버퍼
     * @param description String - 버퍼 설명
     */
    private void logBufferState(ByteBuffer buffer, String description) { // private 메서드 - 버퍼 상태 로깅
        if (buffer != null && logger.isLoggable(Level.FINEST)) { // 버퍼가 존재하고 FINEST 레벨이 활성화된 경우에만 실행
            logger.finest(String.format("%s - position: %d, limit: %d, capacity: %d, remaining: %d",
                    description, // 버퍼 설명
                    buffer.position(), // 현재 위치
                    buffer.limit(), // 제한 위치
                    buffer.capacity(), // 전체 용량
                    buffer.remaining())); // 남은 데이터 크기
        }
    }

    /**
     * HTTP 메서드가 바디를 가질 수 있는지 확인하는 메서드
     * GET, HEAD, DELETE 등은 일반적으로 바디를 가지지 않음
     *
     * @param method String - HTTP 메서드
     * @return boolean - 바디를 가질 수 있으면 true
     */
    private boolean canHaveBody(String method) { // private 메서드 - HTTP 메서드별 바디 허용 여부 확인
        if (method == null) { // null 체크
            return false; // null 메서드는 바디 불허
        }

        // 바디를 가질 수 없는 메서드들
        String upperMethod = method.toUpperCase(); // 대문자로 변환하여 비교
        return !("GET".equals(upperMethod) || // GET 메서드
                "HEAD".equals(upperMethod) || // HEAD 메서드
                "DELETE".equals(upperMethod) || // DELETE 메서드
                "TRACE".equals(upperMethod) || // TRACE 메서드
                "OPTIONS".equals(upperMethod)); // OPTIONS 메서드
    }

    /**
     * Content-Length 헤더 값을 안전하게 파싱하는 메서드
     * 잘못된 값이나 너무 큰 값에 대한 보안 검증 포함
     *
     * @param contentLengthHeader String - Content-Length 헤더 값
     * @return long - 파싱된 Content-Length, 파싱 실패 시 -1
     */
    private long parseContentLength(String contentLengthHeader) { // private 메서드 - Content-Length 안전 파싱
        if (contentLengthHeader == null || contentLengthHeader.trim().isEmpty()) { // null 또는 빈 값 체크
            return -1; // 헤더가 없으면 -1 반환
        }

        try {
            long contentLength = Long.parseLong(contentLengthHeader.trim()); // Long.parseLong() - 문자열을 long으로 변환

            // 음수 값 체크
            if (contentLength < 0) { // 음수 Content-Length는 유효하지 않음
                logger.warning("음수 Content-Length 감지: " + contentLength); // 음수 값 경고
                return -1; // 유효하지 않은 값
            }

            // 최대 크기 제한 (예: 100MB)
            if (contentLength > 100 * 1024 * 1024) { // 100MB 제한
                logger.warning("Content-Length가 너무 큼: " + contentLength); // 크기 제한 초과 경고
                return -1; // 제한 초과
            }

            return contentLength; // 유효한 Content-Length 반환

        } catch (NumberFormatException e) { // 숫자 파싱 실패
            logger.warning("Content-Length 파싱 실패: " + contentLengthHeader); // 파싱 실패 경고
            return -1; // 파싱 실패
        }
    }

    /**
     * HTTP 헤더 이름의 유효성을 확인하는 메서드
     * HTTP 표준에 따른 헤더 이름 검증
     *
     * @param headerName String - 검증할 헤더 이름
     * @return boolean - 유효한 헤더 이름이면 true
     */
    private boolean isValidHeaderName(String headerName) { // private 메서드 - 헤더 이름 유효성 검증
        if (headerName == null || headerName.isEmpty()) { // null 또는 빈 이름 체크
            return false; // 빈 헤더 이름은 유효하지 않음
        }

        // HTTP 헤더 이름은 영문자, 숫자, 하이픈(-), 언더스코어(_)만 허용
        for (int i = 0; i < headerName.length(); i++) { // 문자열의 모든 문자 검사
            char c = headerName.charAt(i); // 현재 문자
            if (!Character.isLetterOrDigit(c) && c != '-' && c != '_') { // 허용되지 않는 문자 확인
                return false; // 유효하지 않은 문자 발견
            }
        }
        return true; // 모든 문자가 유효함
    }

    /**
     * 핸들러의 통계 정보를 반환하는 메서드
     * 모니터링과 디버깅에 활용
     *
     * @return Map<String, Object> - 핸들러 통계 정보
     */
    public Map<String, Object> getStatistics() { // public 메서드 - 핸들러 통계 조회
        Map<String, Object> stats = new HashMap<>(); // 통계를 담을 맵 생성

        // 스레드풀 관련 통계
        stats.put("active", isActive()); // 핸들러 활성 상태
        stats.put("executorShutdown", responseExecutor.isShutdown()); // 스레드풀 종료 상태
        stats.put("executorTerminated", responseExecutor.isTerminated()); // 스레드풀 완전 종료 상태

        // 설정 정보
        stats.put("maxRequestSize", MAX_REQUEST_SIZE); // 최대 요청 크기 제한
        stats.put("shutdownTimeout", SHUTDOWN_TIMEOUT_SECONDS); // 종료 대기 시간

        return stats; // 통계 맵 반환
    }

    // ========== toString 메서드 ==========

    /**
     * HttpChannelHandler의 문자열 표현
     * 디버깅과 로깅에 유용한 정보 제공
     *
     * @return String - 핸들러 정보를 담은 문자열
     */
    @Override
    public String toString() { // Object.toString() 메서드 오버라이드
        return String.format("HttpChannelHandler{server=%s, router=%s, executor=%s, active=%s}",
                server != null ? server.getClass().getSimpleName() : "null", // 서버 클래스명
                router != null ? router.getClass().getSimpleName() : "null", // 라우터 클래스명
                responseExecutor.isShutdown() ? "shutdown" : "active", // 스레드풀 상태
                isActive()); // 핸들러 활성 상태
    }
}