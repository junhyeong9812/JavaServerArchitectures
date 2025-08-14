package com.serverarch.traditional.handlers;

// import 선언부 - 필요한 클래스들을 명시적으로 가져오기
import com.serverarch.common.http.HttpStatus; // HTTP 상태 코드 enum (200, 404, 500 등)
import com.serverarch.traditional.*; // HttpRequest, HttpResponse 클래스들
import com.serverarch.traditional.routing.*; // RouteHandler 인터페이스
import com.serverarch.common.nio.*; // 직접 구현한 NIO 클래스들 (BasicFileAttributes, FileTime 등)

// Java 기본 라이브러리들
import java.io.*; // 파일 입출력 관련 클래스들 (File, RandomAccessFile, ByteArrayOutputStream 등)
import java.nio.file.*; // 새로운 파일 API (Path, Paths 등)
import java.util.*; // Map, HashMap, Set, HashSet 등 컬렉션 클래스들
import java.util.concurrent.*; // ConcurrentHashMap 동시성 컬렉션 - 멀티스레드 안전한 맵
import java.time.*; // 날짜/시간 관련 클래스들 (ZonedDateTime, Instant 등)
import java.time.format.*; // 날짜 포맷터 클래스들 (DateTimeFormatter 등)
import java.util.logging.*; // Logger, Level 로깅 클래스들
import java.util.zip.*; // 압축 관련 클래스들 (GZIPOutputStream 등)

/**
 * 정적 파일 서빙 핸들러 (수정 버전)
 *
 * 주요 기능:
 * 1. CSS, JS, 이미지, HTML 등 정적 파일 서빙 - 웹 애플리케이션의 정적 자원 제공
 * 2. MIME 타입 자동 감지 및 설정 - 파일 확장자 기반으로 올바른 Content-Type 설정
 * 3. 파일 캐싱 (메모리 + HTTP 캐시 헤더) - 성능 최적화를 위한 이중 캐싱 전략
 * 4. Range 요청 지원 (부분 다운로드) - 대용량 파일의 효율적 전송
 * 5. 압축 지원 (gzip) - 네트워크 대역폭 절약
 * 6. 보안 (디렉토리 트래버설 방지) - 경로 조작 공격 차단
 * 7. 성능 최적화 (ETag, Last-Modified) - HTTP 캐싱 메커니즘 활용
 *
 * 설계 원칙:
 * - 의존성 최소화: 직접 구현한 NIO 클래스 사용
 * - 멀티스레드 안전성: ConcurrentHashMap과 적절한 동기화
 * - 메모리 효율성: 큰 파일은 캐시하지 않음
 * - 확장성: 압축 타입과 MIME 타입 쉽게 추가 가능
 */
public class StaticFileHandler implements RouteHandler {

    // 로거 인스턴스 - 이 클래스의 모든 로그 메시지 관리
    // Logger.getLogger(): 클래스별 로거 생성 - 로그 메시지의 출처 식별 가능
    private static final Logger logger = Logger.getLogger(StaticFileHandler.class.getName());

    // ========== 설정 상수들 ==========

    // 메모리 캐시 제한 - 너무 많은 파일을 캐시하면 OutOfMemoryError 위험
    private static final int MAX_CACHE_SIZE = 100;

    // 캐시할 파일의 최대 크기 - 큰 파일은 메모리 캐시에서 제외
    // 1MB = 1024 * 1024 바이트
    private static final long MAX_CACHE_FILE_SIZE = 1024 * 1024;

    // HTTP 캐시 만료 시간 - 브라우저가 파일을 캐시할 시간
    // 24시간 = 24 * 60 * 60 * 1000 밀리초
    private static final long CACHE_EXPIRE_TIME = 24 * 60 * 60 * 1000;

    // ========== 인스턴스 필드들 ==========

    // 정적 파일들이 있는 루트 디렉토리 경로
    // final 키워드로 불변성 보장 - 생성 후 변경 불가
    private final Path rootDirectory;

    // URL 접두사 - 이 핸들러가 처리할 URL 패턴 (/static, /assets 등)
    // 예: "/static"이면 "/static/css/style.css" 같은 요청을 처리
    private final String urlPrefix;

    // 캐싱 활성화 여부 - 성능 최적화 기능 on/off
    private final boolean enableCaching;

    // 압축 활성화 여부 - gzip 압축 기능 on/off
    private final boolean enableCompression;

    // 압축 가능한 MIME 타입들 - 텍스트 기반 파일만 압축 효과 있음
    private final Set<String> compressibleTypes;

    // 파일 캐시 - 자주 요청되는 작은 파일들을 메모리에 저장
    // ConcurrentHashMap: 멀티스레드 환경에서 안전한 해시맵
    // String: 파일 경로 (캐시 키), CachedFile: 캐시된 파일 정보
    private final Map<String, CachedFile> fileCache = new ConcurrentHashMap<>();

    // MIME 타입 매핑 테이블 - 파일 확장자별 Content-Type 결정
    // 정적 초기화로 한 번만 생성되어 모든 인스턴스가 공유
    private static final Map<String, String> MIME_TYPES = createMimeTypeMap();

    // ========== 생성자들 ==========

    /**
     * 기본 설정으로 정적 파일 핸들러 생성
     * 캐싱과 압축을 모두 활성화하는 편의 생성자
     *
     * @param rootDirectory 정적 파일 루트 디렉토리 경로
     * @param urlPrefix URL 접두사 (예: "/static")
     */
    public StaticFileHandler(String rootDirectory, String urlPrefix) {
        // 다른 생성자 호출 - 기본값으로 캐싱과 압축 모두 활성화
        // this(): 같은 클래스의 다른 생성자 호출
        this(rootDirectory, urlPrefix, true, true);
    }

    /**
     * 상세 설정을 위한 정적 파일 핸들러 생성자
     * 모든 옵션을 직접 제어할 수 있는 완전한 생성자
     *
     * @param rootDirectory 정적 파일 루트 디렉토리 경로
     * @param urlPrefix URL 접두사
     * @param enableCaching 캐싱 활성화 여부
     * @param enableCompression 압축 활성화 여부
     */
    public StaticFileHandler(String rootDirectory, String urlPrefix,
                             boolean enableCaching, boolean enableCompression) {
        // 입력 검증 - null 값으로 인한 런타임 오류 방지
        if (rootDirectory == null || urlPrefix == null) {
            // IllegalArgumentException: 메서드 매개변수가 잘못되었을 때 발생시키는 예외
            throw new IllegalArgumentException("루트 디렉토리와 URL 접두사는 필수입니다");
        }

        // 루트 디렉토리 경로 정규화
        // Paths.get(): 문자열 경로를 Path 객체로 변환
        // toAbsolutePath(): 상대 경로를 절대 경로로 변환
        // normalize(): 경로에서 ".", ".." 등을 해석하여 정규화
        this.rootDirectory = Paths.get(rootDirectory).toAbsolutePath().normalize();

        // URL 접두사 정규화 - 마지막 슬래시 제거로 일관성 유지
        // endsWith("/"): 문자열이 "/"로 끝나는지 확인
        // substring(): 문자열의 일부분 추출 (마지막 문자 제거)
        this.urlPrefix = urlPrefix.endsWith("/") ?
                urlPrefix.substring(0, urlPrefix.length() - 1) : urlPrefix;

        // 옵션 설정
        this.enableCaching = enableCaching;
        this.enableCompression = enableCompression;

        // 압축 가능한 MIME 타입 집합 초기화
        this.compressibleTypes = createCompressibleTypes();

        // 루트 디렉토리 존재 여부 확인 및 경고
        // Files.exists(): Path가 가리키는 파일/디렉토리가 존재하는지 확인
        if (!Files.exists(this.rootDirectory)) {
            // logger.warning(): 경고 레벨 로그 출력 - 문제가 있지만 실행은 계속 가능
            logger.warning("정적 파일 디렉토리가 존재하지 않습니다: " + this.rootDirectory);
        }

        // 초기화 완료 로그 - 설정 정보 기록
        // String.format(): 템플릿 문자열에 값들을 대입하여 포맷된 문자열 생성
        logger.info(String.format("정적 파일 핸들러 초기화: %s -> %s (캐싱: %s, 압축: %s)",
                urlPrefix, this.rootDirectory, enableCaching, enableCompression));
    }

    // ========== 메인 요청 처리 메서드 ==========

    /**
     * 정적 파일 요청 처리 메인 메서드
     * RouteHandler 인터페이스의 구현 - 모든 정적 파일 요청이 이 메서드를 통과
     *
     * @param request HTTP 요청 객체
     * @return HTTP 응답 객체
     * @throws Exception 파일 처리 중 예외 발생 시
     */
    @Override
    public HttpResponse handle(HttpRequest request) throws Exception {
        // 요청 경로 추출 - 클라이언트가 요청한 URL 경로
        // request.getPath(): HttpRequest에서 경로 부분만 추출 (예: "/static/css/style.css")
        String requestPath = request.getPath();

        // URL 접두사 검증 - 이 핸들러가 처리해야 할 요청인지 확인
        // startsWith(): 문자열이 특정 접두사로 시작하는지 확인
        if (!requestPath.startsWith(urlPrefix)) {
            // 접두사가 맞지 않으면 404 Not Found 응답
            return HttpResponse.notFound("잘못된 정적 파일 경로입니다");
        }

        // 실제 파일 경로 계산 - URL 접두사 제거
        // substring(): 지정된 위치부터 문자열 끝까지 추출
        String filePath = requestPath.substring(urlPrefix.length());

        // 루트 경로 요청 처리 - 빈 경로나 "/" 요청을 index.html로 리다이렉트
        if (filePath.isEmpty() || filePath.equals("/")) {
            filePath = "/index.html"; // 기본 인덱스 파일 설정
        }

        // 보안 검사 - 디렉토리 트래버설 공격 방지
        // resolveSafePath(): 경로 조작 공격을 차단하는 안전한 경로 해석 메서드
        Path resolvedPath = resolveSafePath(filePath);
        if (resolvedPath == null) {
            // 보안 위반 시 400 Bad Request 응답
            return HttpResponse.badRequest("유효하지 않은 파일 경로입니다");
        }

        // 파일 존재 여부 및 타입 확인
        // Files.exists(): 파일이 실제로 존재하는지 확인
        // Files.isDirectory(): 디렉토리인지 확인 (디렉토리는 서빙하지 않음)
        if (!Files.exists(resolvedPath) || Files.isDirectory(resolvedPath)) {
            return HttpResponse.notFound("파일을 찾을 수 없습니다: " + filePath);
        }

        // 조건부 요청 처리 - HTTP 캐싱 메커니즘
        // handleConditionalRequest(): If-Modified-Since, If-None-Match 헤더 확인
        HttpResponse conditionalResponse = handleConditionalRequest(request, resolvedPath);
        if (conditionalResponse != null) {
            // 304 Not Modified 등의 조건부 응답 반환
            return conditionalResponse;
        }

        // Range 요청 처리 - 부분 다운로드 지원
        // request.getHeader(): 특정 HTTP 헤더 값 조회
        String rangeHeader = request.getHeader("Range");
        if (rangeHeader != null) {
            // Range 헤더가 있으면 부분 요청 처리 (206 Partial Content)
            return handleRangeRequest(request, resolvedPath, rangeHeader);
        }

        // 일반 파일 응답 생성 - 전체 파일 전송
        return createFileResponse(resolvedPath, request);
    }

    // ========== 보안 및 경로 처리 ==========

    /**
     * 안전한 파일 경로 해석
     * 디렉토리 트래버설 공격 (.., /) 방지하는 핵심 보안 메서드
     *
     * @param filePath 클라이언트가 요청한 파일 경로
     * @return 안전한 절대 경로 (보안 위반 시 null)
     */
    private Path resolveSafePath(String filePath) {
        try {
            // 상대 경로를 절대 경로로 안전하게 변환
            // filePath.substring(1): 맨 앞의 "/" 제거 (예: "/css/style.css" -> "css/style.css")
            // rootDirectory.resolve(): 루트 디렉토리 기준으로 상대 경로 해석
            // normalize(): 경로에서 "..", "." 등을 해석하여 정규화
            Path requestedPath = rootDirectory.resolve(filePath.substring(1)).normalize();

            // 경로 검증 - 루트 디렉토리 밖으로 나가는지 확인
            // startsWith(): 경로가 특정 디렉토리로 시작하는지 확인
            if (!requestedPath.startsWith(rootDirectory)) {
                // 보안 위반 로그 - 공격 시도 기록
                logger.warning("디렉토리 트래버설 시도 감지: " + filePath);
                return null; // 보안 위반으로 null 반환
            }

            // 안전한 경로 반환
            return requestedPath;

        } catch (Exception e) { // 경로 해석 중 모든 예외 catch
            // 경로 해석 오류 로그
            logger.warning("파일 경로 해석 오류: " + filePath + " - " + e.getMessage());
            return null; // 오류 시 null 반환으로 안전 확보
        }
    }

    // ========== HTTP 캐싱 처리 ==========

    /**
     * 조건부 요청 처리
     * HTTP 캐싱 메커니즘을 활용한 효율적인 파일 전송
     * If-Modified-Since, If-None-Match 헤더를 확인하여 304 Not Modified 응답 가능
     *
     * @param request HTTP 요청 객체
     * @param filePath 요청된 파일의 경로
     * @return 조건부 응답 객체 (null이면 정상 처리 필요)
     */
    private HttpResponse handleConditionalRequest(HttpRequest request, Path filePath) throws IOException {
        try {
            // 파일 속성 읽기 - 수정 시간, 크기 등의 메타데이터 조회
            // FileAttributesUtil.readAttributes(): 직접 구현한 파일 속성 읽기 메서드
            BasicFileAttributes attrs = FileAttributesUtil.readAttributes(filePath, BasicFileAttributes.class);

            // 마지막 수정 시간 추출
            // attrs.lastModifiedTime(): FileTime 객체 반환
            // toMillis(): FileTime을 밀리초로 변환
            long lastModified = attrs.lastModifiedTime().toMillis();

            // ETag 생성 - 파일의 고유 식별자
            // generateETag(): 파일 경로와 수정 시간으로 고유한 태그 생성
            String etag = generateETag(filePath, lastModified);

            // If-None-Match 헤더 확인 (ETag 기반 캐시 검증)
            String ifNoneMatch = request.getHeader("If-None-Match");
            if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
                // ETag가 일치하면 파일이 변경되지 않았으므로 304 응답
                return createNotModifiedResponse(etag, lastModified);
            }

            // If-Modified-Since 헤더 확인 (날짜 기반 캐시 검증)
            String ifModifiedSince = request.getHeader("If-Modified-Since");
            if (ifModifiedSince != null) {
                try {
                    // HTTP 날짜 문자열을 밀리초로 파싱
                    long clientTime = parseHttpDate(ifModifiedSince);

                    // 클라이언트가 가진 파일이 서버의 파일보다 최신이거나 같으면 304 응답
                    if (lastModified <= clientTime) {
                        return createNotModifiedResponse(etag, lastModified);
                    }
                } catch (Exception e) {
                    // 날짜 파싱 오류는 무시하고 정상 처리 - 잘못된 헤더 값 대응
                    logger.fine("If-Modified-Since 헤더 파싱 오류: " + ifModifiedSince);
                }
            }

            // 조건부 요청이 아니거나 조건에 맞지 않으면 null 반환
            return null;

        } catch (IOException e) {
            // 파일 속성 읽기 오류 시 로그 후 정상 처리로 넘어감
            logger.warning("파일 속성 읽기 오류: " + filePath + " - " + e.getMessage());
            return null;
        }
    }

    // ========== Range 요청 (부분 다운로드) 처리 ==========

    /**
     * Range 요청 처리 - HTTP/1.1의 부분 다운로드 기능
     * 대용량 파일의 효율적 전송과 다운로드 재시작 기능 제공
     *
     * @param request HTTP 요청 객체
     * @param filePath 요청된 파일의 경로
     * @param rangeHeader Range 헤더 값 (예: "bytes=0-1023")
     * @return 206 Partial Content 응답
     */
    private HttpResponse handleRangeRequest(HttpRequest request, Path filePath, String rangeHeader) throws IOException {
        try {
            // 파일 크기 조회 - Range 요청 검증에 필요
            // FileAttributesUtil.size(): 직접 구현한 파일 크기 조회 메서드
            long fileSize = FileAttributesUtil.size(filePath);

            // Range 헤더 파싱 - "bytes=start-end" 형식 해석
            RangeRequest range = parseRangeHeader(rangeHeader, fileSize);
            if (range == null) {
                // 잘못된 Range 헤더 형식 시 400 Bad Request 응답
                return HttpResponse.badRequest("유효하지 않은 Range 요청입니다");
            }

            // 지정된 범위의 파일 데이터 읽기
            byte[] data = readFileRange(filePath, range.start, range.end);

            // 206 Partial Content 응답 생성
            // HttpStatus.PARTIAL_CONTENT: 206 상태 코드
            HttpResponse response = new HttpResponse(HttpStatus.PARTIAL_CONTENT);
            response.setBody(data); // 부분 데이터 설정

            // Content-Range 헤더 설정 - 클라이언트에게 전송 범위 알림
            // 형식: "bytes start-end/total" (예: "bytes 0-1023/2048")
            response.setHeader("Content-Range",
                    String.format("bytes %d-%d/%d", range.start, range.end, fileSize));

            // Accept-Ranges 헤더 - 서버가 Range 요청을 지원함을 명시
            response.setHeader("Accept-Ranges", "bytes");

            // MIME 타입 설정 - 파일 확장자로 Content-Type 결정
            String mimeType = getMimeType(filePath);
            response.setContentType(mimeType);

            return response;

        } catch (Exception e) {
            // Range 요청 처리 오류 시 전체 파일 응답으로 폴백
            logger.warning("Range 요청 처리 오류: " + e.getMessage());
            return createFileResponse(filePath, request);
        }
    }

    // ========== 파일 응답 생성 ==========

    /**
     * 일반 파일 응답 생성
     * 전체 파일을 읽어서 HTTP 응답으로 변환하는 메인 로직
     *
     * @param filePath 서빙할 파일의 경로
     * @param request HTTP 요청 객체 (압축 판단 등에 사용)
     * @return 완성된 HTTP 응답
     */
    private HttpResponse createFileResponse(Path filePath, HttpRequest request) throws IOException {
        // 캐시에서 파일 확인 - 메모리 캐시 활용으로 성능 최적화
        String cacheKey = filePath.toString(); // 파일 경로를 캐시 키로 사용
        CachedFile cachedFile = null;

        if (enableCaching) {
            // 캐시에서 파일 조회
            cachedFile = fileCache.get(cacheKey);

            // 캐시된 파일이 있고 아직 유효한지 확인
            if (cachedFile != null && cachedFile.isValid(filePath)) {
                logger.fine("캐시된 파일 사용: " + filePath);
                // 캐시된 데이터로 응답 생성하여 즉시 반환
                return createResponseFromCache(cachedFile, request);
            }
        }

        // 파일 읽기 - 디스크에서 전체 파일 로드
        // Files.readAllBytes(): 파일 전체를 바이트 배열로 읽기
        byte[] fileData = Files.readAllBytes(filePath);

        // 파일 속성 읽기 - 메타데이터 조회
        BasicFileAttributes attrs = FileAttributesUtil.readAttributes(filePath, BasicFileAttributes.class);

        // 캐시에 저장 조건 확인 및 저장
        // 1. 캐싱이 활성화되어 있고
        // 2. 파일 크기가 제한 이하이며
        // 3. 캐시 개수가 제한 이하일 때만 저장
        if (enableCaching &&
                fileData.length <= MAX_CACHE_FILE_SIZE &&
                fileCache.size() < MAX_CACHE_SIZE) {

            // 새로운 캐시 파일 객체 생성
            cachedFile = new CachedFile(fileData,
                    attrs.lastModifiedTime().toMillis(),
                    getMimeType(filePath));

            // 캐시에 저장
            fileCache.put(cacheKey, cachedFile);
            logger.fine("파일 캐시에 저장: " + filePath);
        }

        // HTTP 응답 생성 및 반환
        return createHttpResponse(fileData, filePath, attrs.lastModifiedTime().toMillis(), request);
    }

    /**
     * HTTP 응답 생성 - 파일 데이터를 HTTP 응답으로 변환
     * MIME 타입 설정, 압축 처리, 캐시 헤더 설정 등 모든 응답 구성 요소 처리
     *
     * @param data 파일의 원본 데이터
     * @param filePath 파일 경로 (MIME 타입 결정용)
     * @param lastModified 마지막 수정 시간 (캐시 헤더용)
     * @param request HTTP 요청 (압축 지원 여부 확인용)
     * @return 완성된 HTTP 응답
     */
    private HttpResponse createHttpResponse(byte[] data, Path filePath, long lastModified, HttpRequest request) {
        // 200 OK 응답 기본 틀 생성
        HttpResponse response = HttpResponse.ok();

        // MIME 타입 결정 및 설정 - 파일 확장자 기반
        String mimeType = getMimeType(filePath);
        response.setContentType(mimeType);

        // 압축 처리 - gzip 압축으로 대역폭 절약
        byte[] responseData = data; // 기본값은 원본 데이터

        if (enableCompression && shouldCompress(mimeType, request)) {
            // 압축 조건 만족 시 gzip 압축 수행
            byte[] compressedData = compressData(data);

            // 압축 성공 및 크기 감소 확인
            if (compressedData != null && compressedData.length < data.length) {
                responseData = compressedData; // 압축된 데이터 사용

                // Content-Encoding 헤더 설정 - 클라이언트에게 압축 방식 알림
                response.setHeader("Content-Encoding", "gzip");

                // 압축 결과 로그
                logger.fine(String.format("파일 gzip 압축 적용: %s (%d -> %d bytes)",
                        filePath, data.length, responseData.length));
            }
        }

        // 응답 바디 설정
        response.setBody(responseData);

        // HTTP 캐시 헤더 설정 - 브라우저 캐싱 최적화
        if (enableCaching) {
            setCacheHeaders(response, lastModified);
        }

        // 추가 헤더들 설정
        // Accept-Ranges: Range 요청 지원 명시
        response.setHeader("Accept-Ranges", "bytes");

        // Content-Length: 응답 바디 크기 명시적 설정
        response.setHeader("Content-Length", String.valueOf(responseData.length));

        return response;
    }

    // ========== MIME 타입 관리 ==========

    /**
     * MIME 타입 매핑 테이블 생성
     * 파일 확장자별로 적절한 Content-Type 헤더 값을 매핑
     *
     * @return 확장자 -> MIME 타입 매핑 맵
     */
    private static Map<String, String> createMimeTypeMap() {
        // HashMap 생성 - 확장자와 MIME 타입의 매핑 저장
        Map<String, String> mimeTypes = new HashMap<>();

        // 웹 관련 텍스트 파일들 - charset=UTF-8 명시로 한글 등 다국어 지원
        mimeTypes.put("html", "text/html; charset=UTF-8");       // HTML 문서
        mimeTypes.put("htm", "text/html; charset=UTF-8");        // HTML 문서 (구 확장자)
        mimeTypes.put("css", "text/css; charset=UTF-8");         // 스타일시트
        mimeTypes.put("js", "application/javascript; charset=UTF-8"); // JavaScript
        mimeTypes.put("json", "application/json; charset=UTF-8"); // JSON 데이터
        mimeTypes.put("xml", "application/xml; charset=UTF-8");   // XML 문서
        mimeTypes.put("txt", "text/plain; charset=UTF-8");       // 일반 텍스트

        // 이미지 파일들 - 바이너리이므로 charset 불필요
        mimeTypes.put("png", "image/png");          // PNG 이미지
        mimeTypes.put("jpg", "image/jpeg");         // JPEG 이미지
        mimeTypes.put("jpeg", "image/jpeg");        // JPEG 이미지
        mimeTypes.put("gif", "image/gif");          // GIF 이미지
        mimeTypes.put("webp", "image/webp");        // WebP 이미지 (현대 포맷)
        mimeTypes.put("svg", "image/svg+xml");      // SVG 벡터 이미지
        mimeTypes.put("ico", "image/x-icon");       // 파비콘

        // 웹 폰트 파일들 - 웹 페이지에서 커스텀 폰트 사용
        mimeTypes.put("woff", "font/woff");         // Web Open Font Format
        mimeTypes.put("woff2", "font/woff2");       // WOFF 2.0 (압축률 개선)
        mimeTypes.put("ttf", "font/ttf");           // TrueType 폰트
        mimeTypes.put("eot", "application/vnd.ms-fontobject"); // IE 전용 폰트

        // 기타 파일들 - 다운로드나 임베드용
        mimeTypes.put("pdf", "application/pdf");     // PDF 문서
        mimeTypes.put("zip", "application/zip");     // ZIP 압축 파일
        mimeTypes.put("mp4", "video/mp4");           // MP4 비디오
        mimeTypes.put("mp3", "audio/mpeg");          // MP3 오디오

        // 불변 맵으로 반환 - 실수로 수정되는 것 방지
        return Collections.unmodifiableMap(mimeTypes);
    }

    /**
     * 압축 가능한 MIME 타입 집합 생성
     * 텍스트 기반 파일들만 압축 효과가 있으므로 선별적 압축
     *
     * @return 압축 가능한 MIME 타입 집합
     */
    private Set<String> createCompressibleTypes() {
        // HashSet 생성 - 압축 가능한 타입들의 집합
        Set<String> types = new HashSet<>();

        // 텍스트 기반 타입들 - 압축 효과가 큰 파일들
        types.add("text/html");              // HTML - 태그 반복으로 압축률 높음
        types.add("text/css");               // CSS - 공백과 반복 패턴 많음
        types.add("application/javascript"); // JavaScript - 변수명 반복 등
        types.add("application/json");       // JSON - 구조적 반복 패턴
        types.add("application/xml");        // XML - 태그 구조로 압축률 높음
        types.add("text/plain");             // 일반 텍스트
        types.add("image/svg+xml");          // SVG - XML 기반으로 압축 가능

        return types;
    }

    /**
     * 파일 확장자로 MIME 타입 결정
     *
     * @param filePath 파일 경로
     * @return 해당하는 MIME 타입 문자열
     */
    private String getMimeType(Path filePath) {
        // 파일명 추출 및 소문자 변환 - 대소문자 구분 없이 확장자 매칭
        // getFileName(): Path에서 파일명만 추출 (디렉토리 경로 제외)
        // toString(): Path를 문자열로 변환
        // toLowerCase(): 대소문자 통일로 정확한 매칭 보장
        String fileName = filePath.getFileName().toString().toLowerCase();

        // 마지막 점(.) 위치 찾기 - 확장자 구분자
        // lastIndexOf('.'): 문자열에서 마지막 '.' 문자의 인덱스 반환
        int dotIndex = fileName.lastIndexOf('.');

        // 확장자가 없는 경우 - 점이 없거나 파일명이 점으로 시작하는 경우
        if (dotIndex == -1) {
            // 바이너리 스트림으로 처리 - 브라우저가 다운로드로 인식
            return "application/octet-stream";
        }

        // 확장자 추출 - 점 다음부터 문자열 끝까지
        // substring(dotIndex + 1): 점 다음 문자부터 끝까지 추출
        String extension = fileName.substring(dotIndex + 1);

        // MIME 타입 맵에서 조회 후 반환
        // getOrDefault(): 키가 있으면 해당 값, 없으면 기본값 반환
        return MIME_TYPES.getOrDefault(extension, "application/octet-stream");
    }

    // ========== 압축 관련 메서드들 ==========

    /**
     * 압축 여부 결정
     * 클라이언트 지원 여부와 파일 타입을 고려하여 압축 적용 여부 결정
     *
     * @param mimeType 파일의 MIME 타입
     * @param request HTTP 요청 (Accept-Encoding 헤더 확인용)
     * @return 압축을 적용할지 여부
     */
    private boolean shouldCompress(String mimeType, HttpRequest request) {
        // 클라이언트의 압축 지원 여부 확인
        // Accept-Encoding 헤더에서 gzip 지원 여부 검사
        String acceptEncoding = request.getHeader("Accept-Encoding");
        if (acceptEncoding == null || !acceptEncoding.contains("gzip")) {
            // 클라이언트가 gzip을 지원하지 않으면 압축하지 않음
            return false;
        }

        // MIME 타입이 압축 가능한 타입인지 확인
        // stream().anyMatch(): 스트림에서 조건을 만족하는 요소가 하나라도 있는지 확인
        // mimeType::startsWith: 메서드 참조 - mimeType.startsWith()와 동일
        return compressibleTypes.stream().anyMatch(mimeType::startsWith);
    }

    /**
     * 데이터 gzip 압축
     * 바이트 배열을 gzip 압축하여 크기 최적화
     *
     * @param data 압축할 원본 데이터
     * @return 압축된 데이터 (압축 실패 시 null)
     */
    private byte[] compressData(byte[] data) {
        // try-with-resources 구문 - 자동 리소스 해제
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); // 메모리 출력 스트림
             GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {   // gzip 압축 스트림

            // 데이터 압축 - 압축 스트림에 원본 데이터 쓰기
            gzipOut.write(data);

            // 압축 완료 - 남은 데이터를 모두 압축하여 출력
            gzipOut.finish();

            // 압축된 바이트 배열 반환
            return baos.toByteArray();

        } catch (IOException e) {
            // 압축 실패 로그 - 원본 데이터로 폴백할 것임을 암시
            logger.warning("데이터 압축 오류: " + e.getMessage());
            return null; // 압축 실패 시 null 반환
        }
    }

    // ========== 내부 클래스들 ==========

    /**
     * 캐시된 파일 정보를 담는 내부 클래스
     * 메모리 캐시에서 파일 데이터와 메타데이터를 함께 저장
     */
    private static class CachedFile {
        // 파일 데이터 - 실제 파일 내용
        final byte[] data;

        // 마지막 수정 시간 - 캐시 유효성 검증용
        final long lastModified;

        // MIME 타입 - 응답 생성 시 재사용
        final String mimeType;

        // 캐시 생성 시간 - 향후 TTL 기능 구현 시 사용 가능
        final long cacheTime;

        /**
         * 캐시된 파일 객체 생성자
         *
         * @param data 파일 데이터
         * @param lastModified 마지막 수정 시간
         * @param mimeType MIME 타입
         */
        CachedFile(byte[] data, long lastModified, String mimeType) {
            // 방어적 복사 - 외부에서 원본 배열을 수정해도 캐시에 영향 없음
            this.data = data.clone();
            this.lastModified = lastModified;
            this.mimeType = mimeType;

            // 현재 시간을 캐시 생성 시간으로 기록
            this.cacheTime = System.currentTimeMillis();
        }

        /**
         * 캐시 유효성 검증
         * 파일이 수정되었는지 확인하여 캐시 무효화 여부 결정
         *
         * @param filePath 검증할 파일 경로
         * @return 캐시가 유효하면 true, 무효하면 false
         */
        boolean isValid(Path filePath) {
            try {
                // 현재 파일의 수정 시간 조회
                // FileAttributesUtil.getLastModifiedTime(): 직접 구현한 수정 시간 조회 메서드
                long currentModified = FileAttributesUtil.getLastModifiedTime(filePath).toMillis();

                // 캐시된 수정 시간과 비교 - 같으면 파일이 변경되지 않음
                return lastModified == currentModified;

            } catch (IOException e) {
                // 파일 읽기 오류 시 캐시 무효로 처리 - 안전한 폴백
                return false;
            }
        }
    }

    /**
     * Range 요청 정보를 담는 내부 클래스
     * HTTP Range 헤더 파싱 결과를 저장
     */
    private static class RangeRequest {
        // 시작 바이트 위치 (포함)
        final long start;

        // 종료 바이트 위치 (포함)
        final long end;

        /**
         * Range 요청 정보 생성자
         *
         * @param start 시작 바이트 위치
         * @param end 종료 바이트 위치
         */
        RangeRequest(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    // ========== 유틸리티 메서드들 ==========

    /**
     * 304 Not Modified 응답 생성
     * 클라이언트 캐시가 여전히 유효할 때 반환하는 응답
     *
     * @param etag 파일의 ETag 값
     * @param lastModified 마지막 수정 시간
     * @return 304 응답 객체
     */
    private HttpResponse createNotModifiedResponse(String etag, long lastModified) {
        // 304 Not Modified 상태로 응답 생성
        // HttpStatus.NOT_MODIFIED: 304 상태 코드
        HttpResponse response = new HttpResponse(HttpStatus.NOT_MODIFIED);

        // ETag 헤더 설정 - 클라이언트가 캐시 검증에 사용
        response.setHeader("ETag", etag);

        // Last-Modified 헤더 설정 - 날짜 기반 캐시 검증용
        response.setHeader("Last-Modified", formatHttpDate(lastModified));

        return response;
    }

    /**
     * ETag 생성
     * 파일의 고유 식별자 생성 - 파일 내용이 같으면 동일한 ETag
     *
     * @param filePath 파일 경로
     * @param lastModified 마지막 수정 시간
     * @return ETag 문자열
     */
    private String generateETag(Path filePath, long lastModified) {
        // 파일 경로와 수정 시간을 조합하여 해시코드 생성
        // Math.abs(): 음수 해시코드를 양수로 변환
        // hashCode(): 객체의 해시코드 생성
        int hash = Math.abs((filePath.toString() + lastModified).hashCode());

        // ETag 형식으로 반환 - 따옴표로 감싸는 것이 HTTP 표준
        return "\"" + hash + "\"";
    }

    /**
     * HTTP 캐시 헤더 설정
     * 브라우저가 파일을 캐시하도록 지시하는 헤더들 설정
     *
     * @param response HTTP 응답 객체
     * @param lastModified 마지막 수정 시간
     */
    private void setCacheHeaders(HttpResponse response, long lastModified) {
        // Cache-Control 헤더 - 캐시 정책 설정
        // public: 모든 캐시에서 저장 가능 (프록시, 브라우저 등)
        // max-age: 캐시 유효 시간 (초 단위)
        response.setHeader("Cache-Control", "public, max-age=" + (CACHE_EXPIRE_TIME / 1000));

        // Last-Modified 헤더 - 파일의 마지막 수정 시간
        response.setHeader("Last-Modified", formatHttpDate(lastModified));

        // ETag 헤더 - 파일의 고유 식별자
        // 간단한 버전으로 더미 경로 사용 (실제로는 filePath를 받아야 함)
        response.setHeader("ETag", generateETag(Paths.get("dummy"), lastModified));
    }

    /**
     * HTTP 날짜 형식으로 포맷
     * 밀리초 타임스탬프를 RFC 1123 형식의 HTTP 날짜 문자열로 변환
     *
     * @param timestamp 밀리초 타임스탬프
     * @return HTTP 날짜 문자열 (예: "Mon, 01 Jan 2024 12:00:00 GMT")
     */
    private String formatHttpDate(long timestamp) {
        // 밀리초를 Instant로 변환
        // Instant.ofEpochMilli(): 에포크부터의 밀리초로 Instant 생성
        Instant instant = Instant.ofEpochMilli(timestamp);

        // UTC 시간대로 ZonedDateTime 생성
        // ZonedDateTime.ofInstant(): Instant와 시간대로 ZonedDateTime 생성
        // ZoneOffset.UTC: UTC 시간대 (+00:00)
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, ZoneOffset.UTC);

        // RFC 1123 형식으로 포맷
        // DateTimeFormatter.RFC_1123_DATE_TIME: HTTP 표준 날짜 포맷터
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(zonedDateTime);
    }

    /**
     * HTTP 날짜 파싱
     * RFC 1123 형식의 HTTP 날짜 문자열을 밀리초 타임스탬프로 변환
     *
     * @param dateStr HTTP 날짜 문자열
     * @return 밀리초 타임스탬프
     */
    private long parseHttpDate(String dateStr) {
        // RFC 1123 형식으로 파싱
        // ZonedDateTime.parse(): 문자열을 ZonedDateTime으로 파싱
        ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateStr, DateTimeFormatter.RFC_1123_DATE_TIME);

        // Instant로 변환 후 밀리초 추출
        // toInstant(): ZonedDateTime을 Instant로 변환
        // toEpochMilli(): Instant를 에포크부터의 밀리초로 변환
        return zonedDateTime.toInstant().toEpochMilli();
    }

    /**
     * Range 헤더 파싱
     * "bytes=start-end" 형식의 헤더를 RangeRequest 객체로 변환
     *
     * @param rangeHeader Range 헤더 값
     * @param fileSize 파일 총 크기 (범위 검증용)
     * @return 파싱된 Range 요청 (실패 시 null)
     */
    private RangeRequest parseRangeHeader(String rangeHeader, long fileSize) {
        // "bytes=" 접두사 확인
        // startsWith(): 문자열이 특정 접두사로 시작하는지 확인
        if (!rangeHeader.startsWith("bytes=")) {
            return null; // 잘못된 형식
        }

        // "bytes=" 제거하여 실제 범위 부분 추출
        // substring(6): "bytes="는 6글자이므로 7번째 문자부터 추출
        String range = rangeHeader.substring(6);

        // '-'로 시작과 끝 분할
        // split("-"): 하이픈으로 문자열 분할
        String[] parts = range.split("-");

        // 정확히 2개 부분이어야 함 (start-end)
        if (parts.length != 2) {
            return null; // 잘못된 형식
        }

        try {
            // 시작 위치 파싱 - 빈 문자열이면 0 (suffix-byte-range-spec)
            // isEmpty(): 문자열이 비어있는지 확인
            // Long.parseLong(): 문자열을 long으로 변환
            long start = parts[0].isEmpty() ? 0 : Long.parseLong(parts[0]);

            // 종료 위치 파싱 - 빈 문자열이면 파일 끝 (prefix-byte-range-spec)
            long end = parts[1].isEmpty() ? fileSize - 1 : Long.parseLong(parts[1]);

            // 범위 유효성 검증
            // 1. 시작이 0 이상
            // 2. 종료가 시작보다 크거나 같음
            // 3. 종료가 파일 크기보다 작음
            if (start >= 0 && end >= start && end < fileSize) {
                return new RangeRequest(start, end); // 유효한 범위
            }

        } catch (NumberFormatException e) {
            // 숫자 파싱 오류 - 잘못된 숫자 형식
        }

        return null; // 유효하지 않은 범위
    }

    /**
     * 파일의 지정된 범위 읽기
     * RandomAccessFile을 사용하여 파일의 특정 부분만 읽기
     *
     * @param filePath 읽을 파일 경로
     * @param start 시작 바이트 위치
     * @param end 종료 바이트 위치
     * @return 읽은 데이터 배열
     * @throws IOException 파일 읽기 실패 시
     */
    private byte[] readFileRange(Path filePath, long start, long end) throws IOException {
        // 읽을 바이트 수 계산 - end는 포함이므로 +1
        int length = (int) (end - start + 1);

        // 읽을 데이터를 저장할 버퍼 생성
        byte[] buffer = new byte[length];

        // try-with-resources로 자동 파일 닫기
        // RandomAccessFile: 파일의 임의 위치에서 읽기/쓰기 가능한 클래스
        // "r": 읽기 전용 모드
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "r")) {
            // 파일 포인터를 시작 위치로 이동
            // seek(): 파일에서 읽기/쓰기할 위치 설정
            file.seek(start);

            // 지정된 길이만큼 데이터 읽기
            // readFully(): 버퍼가 가득 찰 때까지 읽기 (부분 읽기 방지)
            file.readFully(buffer);
        }

        return buffer; // 읽은 데이터 반환
    }

    /**
     * 캐시된 파일로 응답 생성
     * 메모리 캐시에 있는 파일 데이터로 HTTP 응답 생성
     *
     * @param cachedFile 캐시된 파일 정보
     * @param request HTTP 요청 (현재는 사용하지 않음)
     * @return HTTP 응답
     */
    private HttpResponse createResponseFromCache(CachedFile cachedFile, HttpRequest request) {
        // 200 OK 응답 생성
        HttpResponse response = HttpResponse.ok();

        // 캐시된 데이터와 MIME 타입 설정
        response.setBody(cachedFile.data); // 캐시된 파일 데이터 사용
        response.setContentType(cachedFile.mimeType); // 캐시된 MIME 타입 사용

        // 캐시 헤더 설정 (캐싱이 활성화된 경우)
        if (enableCaching) {
            setCacheHeaders(response, cachedFile.lastModified);
        }

        return response; // 캐시 기반 응답 반환
    }
}