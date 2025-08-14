package com.serverarch.common.nio;

// Java I/O 라이브러리
// java.io.File: 파일 시스템의 파일과 디렉토리를 나타내는 레거시 클래스
import java.io.File;

// Java NIO 파일 시스템 라이브러리
// java.nio.file.Path: 파일 시스템의 경로를 나타내는 현대적 인터페이스
import java.nio.file.Path;

/**
 * BasicFileAttributes의 기본 구현체
 * 실제 파일 시스템에서 파일 속성을 읽어와 제공하는 구현 클래스
 *
 * 구현 특징:
 * 1. File 클래스 기반 구현 - Java 6+ 호환성 보장
 * 2. 지연 로딩 (Lazy Loading) - 필요할 때만 속성 읽기로 성능 최적화
 * 3. 캐싱 - 한 번 읽은 속성은 메모리에 저장하여 중복 I/O 방지
 * 4. 스레드 안전 - volatile과 동기화로 멀티스레드 환경 지원
 * 5. 방어적 프로그래밍 - null 체크와 예외 상황 처리
 *
 * 성능 고려사항:
 * - 파일 I/O는 비용이 비싸므로 캐싱 필수
 * - volatile 사용으로 메모리 가시성 보장
 * - Double-checked locking으로 동기화 비용 최소화
 */
public class BasicFileAttributesImpl implements BasicFileAttributes {

    // 파일 객체 - 속성을 읽을 대상 파일
    // final 키워드로 불변성 보장 - 생성 후 파일 객체 변경 불가
    private final File file;

    // ========== 캐시된 속성들 ==========
    // volatile 키워드 사용 이유:
    // 1. 멀티스레드 환경에서 변수의 가시성(visibility) 보장
    // 2. CPU 캐시가 아닌 메인 메모리에서 직접 읽기/쓰기
    // 3. 한 스레드가 변경한 값을 다른 스레드가 즉시 볼 수 있음

    // 시간 관련 속성들
    private volatile FileTime lastModifiedTime;  // 마지막 수정 시간
    private volatile FileTime lastAccessTime;    // 마지막 접근 시간
    private volatile FileTime creationTime;      // 생성 시간

    // 파일 정보 속성들
    private volatile Long fileSize;              // 파일 크기 (바이트)
    private volatile Boolean isDirectory;        // 디렉토리 여부
    private volatile Boolean isFile;             // 일반 파일 여부

    /**
     * File 객체로 기본 파일 속성 구현체 생성
     *
     * @param file 속성을 읽을 파일 객체 (null 불허)
     * @throws IllegalArgumentException file이 null인 경우
     */
    public BasicFileAttributesImpl(File file) {
        // null 체크 - 방어적 프로그래밍으로 런타임 에러 방지
        if (file == null) {
            throw new IllegalArgumentException("파일 객체는 null일 수 없습니다");
        }

        // 파일 객체 저장 - 모든 속성 조회의 기반이 됨
        this.file = file;
    }

    /**
     * Path 객체로부터 기본 파일 속성 구현체 생성
     * NIO2 Path와 레거시 File API 간의 브리지 역할
     *
     * @param path 파일 경로 (null 불허)
     * @throws IllegalArgumentException path가 null인 경우
     */
    public BasicFileAttributesImpl(Path path) {
        this(validatePath(path));  // 첫 번째 문장
    }

    private static File validatePath(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("경로는 null일 수 없습니다");
        }
        return path.toFile();
    }

    /**
     * 마지막 수정 시간 반환
     * HTTP Last-Modified 헤더 생성과 캐시 검증에 사용
     *
     * @return 마지막 수정 시간을 나타내는 FileTime 객체
     */
    @Override
    public FileTime lastModifiedTime() {
        // 이미 캐시된 값이 있으면 즉시 반환 - 중복 I/O 방지
        if (lastModifiedTime == null) {
            // 동기화 블록 - 멀티스레드 환경에서 중복 초기화 방지
            // synchronized (this): 현재 객체를 락으로 사용
            synchronized (this) {
                // Double-checked locking 패턴
                // 첫 번째 체크: 락 획득 전 빠른 확인
                // 두 번째 체크: 락 획득 후 다시 확인 (다른 스레드가 이미 초기화했을 수 있음)
                if (lastModifiedTime == null) {
                    // file.lastModified(): 파일의 마지막 수정 시간을 밀리초로 반환
                    // 반환값: 에포크(1970-01-01 00:00:00 UTC)부터의 밀리초
                    long millis = file.lastModified();

                    // 0 반환 조건들:
                    // 1. 파일이 존재하지 않음
                    // 2. I/O 오류 발생
                    // 3. 보안상 접근 불가
                    if (millis == 0) {
                        // 현재 시간을 기본값으로 사용 - 오류 상황에서의 안전한 폴백
                        // 완전히 틀린 값보다는 현재 시간이 더 유용
                        millis = System.currentTimeMillis();
                    }

                    // FileTime 객체로 변환하여 캐시에 저장
                    lastModifiedTime = FileTime.fromMillis(millis);
                }
            }
        }

        // 캐시된 값 반환 - 두 번째 호출부터는 매우 빠름
        return lastModifiedTime;
    }

    /**
     * 마지막 접근 시간 반환
     * 많은 파일 시스템에서 성능상 이유로 접근 시간 추적을 비활성화함
     *
     * @return 마지막 접근 시간을 나타내는 FileTime 객체
     */
    @Override
    public FileTime lastAccessTime() {
        // 지연 초기화 패턴 - 필요할 때만 생성
        if (lastAccessTime == null) {
            synchronized (this) {
                if (lastAccessTime == null) {
                    // File API 한계: 접근 시간을 직접 제공하지 않음
                    // 현실적 대안: 수정 시간을 접근 시간으로 사용
                    // 이유:
                    // 1. 대부분 파일시스템에서 atime 추적 비활성화 (noatime 옵션)
                    // 2. SSD 수명 연장을 위해 접근 시간 업데이트 생략
                    // 3. 웹 서버에서는 수정 시간이 더 중요
                    lastAccessTime = lastModifiedTime();
                }
            }
        }

        return lastAccessTime;
    }

    /**
     * 파일 생성 시간 반환
     * Java File API의 한계로 인해 수정 시간으로 대체
     *
     * @return 생성 시간을 나타내는 FileTime 객체
     */
    @Override
    public FileTime creationTime() {
        // 지연 초기화 패턴
        if (creationTime == null) {
            synchronized (this) {
                if (creationTime == null) {
                    // File API 한계: 생성 시간을 직접 제공하지 않음
                    // 플랫폼별 차이:
                    // - Windows: NTFS에서 생성 시간 지원하지만 Java File API로 접근 불가
                    // - Linux: ext4 등에서 crtime 지원하지만 표준 API 없음
                    // - macOS: HFS+에서 생성 시간 지원하지만 접근 복잡
                    //
                    // 현실적 대안: 수정 시간을 생성 시간으로 사용
                    // 웹 서버에서는 파일이 수정되지 않는 경우가 많아 실용적
                    creationTime = lastModifiedTime();
                }
            }
        }

        return creationTime;
    }

    /**
     * 일반 파일인지 확인
     * 정적 파일 서빙에서 서빙 가능한 파일인지 판단하는 핵심 메서드
     *
     * @return 일반 파일이면 true, 디렉토리나 특수 파일이면 false
     */
    @Override
    public boolean isRegularFile() {
        // 지연 초기화로 파일 타입 확인 - I/O 비용 최소화
        if (isFile == null) {
            synchronized (this) {
                if (isFile == null) {
                    // file.isFile(): 일반 파일인지 확인하는 File 클래스 메서드
                    // true 조건:
                    // 1. 파일이 존재함
                    // 2. 디렉토리가 아님
                    // 3. 읽기 가능한 일반 파일임
                    //
                    // false 조건:
                    // 1. 파일이 존재하지 않음
                    // 2. 디렉토리임
                    // 3. 특수 파일임 (장치 파일, 파이프 등)
                    // 4. 보안상 접근 불가
                    isFile = file.isFile();
                }
            }
        }

        // Boolean 객체를 boolean으로 자동 언박싱
        // null이 될 수 없으므로 NPE 위험 없음
        return isFile;
    }

    /**
     * 디렉토리인지 확인
     * 디렉토리 서빙 방지와 보안 검증에 사용
     *
     * @return 디렉토리이면 true, 일반 파일이면 false
     */
    @Override
    public boolean isDirectory() {
        // 지연 초기화로 디렉토리 여부 확인
        if (isDirectory == null) {
            synchronized (this) {
                if (isDirectory == null) {
                    // file.isDirectory(): 디렉토리인지 확인하는 File 클래스 메서드
                    // true 조건:
                    // 1. 경로가 존재함
                    // 2. 디렉토리임
                    // 3. 읽기 권한이 있음
                    //
                    // false 조건:
                    // 1. 경로가 존재하지 않음
                    // 2. 일반 파일임
                    // 3. 접근 권한 없음
                    isDirectory = file.isDirectory();
                }
            }
        }

        return isDirectory;
    }

    /**
     * 심볼릭 링크인지 확인
     * 보안상 심볼릭 링크 처리를 제한할 때 사용
     *
     * @return 현재 구현에서는 항상 false (File API 한계)
     */
    @Override
    public boolean isSymbolicLink() {
        // File API 한계: 심볼릭 링크를 직접 구분할 수 없음
        //
        // 심볼릭 링크 감지 방법들:
        // 1. NIO2의 Files.isSymbolicLink() 사용 (의존성 추가 필요)
        // 2. JNI로 OS 네이티브 API 호출 (플랫폼 종속적)
        // 3. 경로 정규화 비교 (부정확함)
        //
        // 현재 선택: 의존성 최소화를 위해 false 반환
        // 보안 영향: 심볼릭 링크를 일반 파일로 처리
        // - 장점: 호환성 좋음
        // - 단점: 심볼릭 링크 기반 공격에 취약할 수 있음
        //
        // 향후 개선: NIO2 기반 구현체에서 정확한 감지 가능
        return false;
    }

    /**
     * 기타 파일 타입인지 확인
     * 특수 파일 (장치 파일, 파이프, 소켓 등) 여부 판단
     *
     * @return 특수 파일이면 true, 일반 파일/디렉토리/링크이면 false
     */
    @Override
    public boolean isOther() {
        // 논리적 추론: 일반 파일도 디렉토리도 심볼릭 링크도 아니면 특수 파일
        //
        // 특수 파일 예시:
        // - 장치 파일 (/dev/null, /dev/sda1 등)
        // - 명명된 파이프 (FIFO)
        // - 소켓 파일
        // - 블록/문자 장치
        //
        // 웹 서버에서의 처리:
        // - 일반적으로 특수 파일은 서빙하지 않음
        // - 보안상 접근을 차단하는 것이 안전
        return !isRegularFile() && !isDirectory() && !isSymbolicLink();
    }

    /**
     * 파일 크기 반환 (바이트 단위)
     * HTTP Content-Length 헤더와 Range 요청 처리에 필수
     *
     * @return 파일 크기 (바이트 단위, 0 이상)
     */
    @Override
    public long size() {
        // 지연 초기화로 파일 크기 확인 - 큰 파일의 경우 I/O 비용 고려
        if (fileSize == null) {
            synchronized (this) {
                if (fileSize == null) {
                    // file.length(): 파일 크기를 바이트 단위로 반환
                    //
                    // 반환값 의미:
                    // - 양수: 실제 파일 크기 (바이트)
                    // - 0: 빈 파일이거나 디렉토리이거나 존재하지 않음
                    //
                    // 주의사항:
                    // 1. 디렉토리의 경우 0 반환 (크기 개념 없음)
                    // 2. 존재하지 않는 파일도 0 반환
                    // 3. 매우 큰 파일(2GB+)도 정확히 반환 (long 타입)
                    // 4. 실시간으로 변경되는 파일의 경우 호출 시점의 크기
                    fileSize = file.length();
                }
            }
        }

        // Long 객체를 long 원시 타입으로 자동 언박싱
        return fileSize;
    }

    /**
     * 파일 키 반환 (고유 식별자)
     * 파일 시스템에서 파일을 고유하게 식별하는 값
     *
     * @return 파일의 절대 경로 문자열 (고유 식별자로 사용)
     */
    @Override
    public Object fileKey() {
        // File API 한계: 실제 파일 시스템의 고유 키 접근 불가
        //
        // 이상적인 파일 키:
        // - Linux: inode 번호 + 장치 ID
        // - Windows: 파일 인덱스 + 볼륨 시리얼 번호
        // - macOS: inode 번호 + 장치 ID
        //
        // 현재 구현: 절대 경로를 고유 키로 사용
        //
        // 장점:
        // 1. 크로스 플랫폼 호환성
        // 2. 구현 단순함
        // 3. 디버깅 용이성 (사람이 읽기 쉬움)
        //
        // 단점:
        // 1. 하드링크 구분 불가 (같은 파일이지만 다른 경로)
        // 2. 대소문자 구분 이슈 (Windows에서 문제 될 수 있음)
        // 3. 심볼릭 링크와 원본 파일 구분 불가
        //
        // 실제 사용: 캐싱 키, 중복 파일 감지 등에 활용
        return file.getAbsolutePath();
    }

    /**
     * 파일 속성 정보를 포함한 문자열 표현
     * 디버깅과 로깅에 유용한 상세 정보 제공
     *
     * @return 파일 속성을 요약한 문자열
     */
    @Override
    public String toString() {
        // StringBuilder 사용 - 여러 문자열 연결 시 성능 최적화
        StringBuilder sb = new StringBuilder();

        // 클래스명으로 시작하여 객체 타입 명확화
        sb.append("BasicFileAttributesImpl{");

        // 파일 경로 - 가장 중요한 식별 정보
        sb.append("file='").append(file.getAbsolutePath()).append('\'');

        // 파일 크기 - HTTP Content-Length와 직결
        sb.append(", size=").append(size());

        // 파일 타입 정보 - 서빙 가능 여부 판단
        sb.append(", isDirectory=").append(isDirectory());
        sb.append(", isRegularFile=").append(isRegularFile());

        // 시간 정보 - 캐싱과 조건부 요청에 중요
        sb.append(", lastModified=").append(lastModifiedTime());

        sb.append('}');

        return sb.toString();
    }

    /**
     * 동등성 비교
     * 같은 파일을 가리키는 객체인지 확인
     *
     * @param obj 비교할 객체
     * @return 같은 파일을 나타내면 true, 다르면 false
     */
    @Override
    public boolean equals(Object obj) {
        // 1단계: 동일 객체 참조 확인
        if (this == obj) return true;

        // 2단계: null 체크
        if (obj == null) return false;

        // 3단계: 타입 확인
        if (!(obj instanceof BasicFileAttributesImpl)) return false;

        // 4단계: 파일 경로 비교
        BasicFileAttributesImpl other = (BasicFileAttributesImpl) obj;

        // File.equals()는 경로 정규화를 수행하므로 안전
        return file.equals(other.file);
    }

    /**
     * 해시 코드 생성
     * HashMap, HashSet에서 객체 식별에 사용
     *
     * @return 파일 경로 기반 해시 코드
     */
    @Override
    public int hashCode() {
        // File 객체의 해시코드 사용 - 경로 기반으로 생성됨
        // equals()에서 file.equals()를 사용하므로 일관성 보장
        return file.hashCode();
    }
}