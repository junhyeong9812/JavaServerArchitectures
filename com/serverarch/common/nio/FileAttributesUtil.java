package com.serverarch.common.nio;

// Java I/O 라이브러리
// java.io.*: 입출력 관련 클래스들
// File: 파일 시스템의 파일과 디렉토리를 나타내는 클래스
// IOException: 입출력 작업 중 발생하는 예외
import java.io.File;
import java.io.IOException;

// Java NIO 파일 시스템 라이브러리
// java.nio.file.Path: 파일 시스템의 경로를 나타내는 인터페이스
import java.nio.file.Path;

/**
 * FileAttributesUtil - 파일 속성 유틸리티 클래스
 * Java NIO2 Files 클래스의 핵심 기능을 모방한 정적 유틸리티 메서드 제공
 *
 * 설계 목표:
 * 1. Java NIO2 API와 호환성 유지 - 기존 코드 이식성 보장
 * 2. 간편한 파일 속성 접근 방법 제공 - 복잡한 객체 생성 없이 빠른 조회
 * 3. 예외 처리 및 오류 복구 내장 - 안전한 파일 작업 보장
 * 4. 성능 최적화 - 필요한 속성만 읽는 최적화된 메서드들
 *
 * 사용 패턴:
 * - StaticFileHandler에서 파일 메타데이터 빠른 조회
 * - 파일 존재 여부 확인
 * - 파일 크기와 수정 시간 조회
 * - 조건부 HTTP 응답 생성
 *
 * 스레드 안전성:
 * - 모든 메서드가 static이고 상태를 갖지 않아 스레드 안전
 * - 각 호출이 독립적이므로 동시 접근 문제 없음
 */
public final class FileAttributesUtil {

    /**
     * 유틸리티 클래스이므로 인스턴스 생성 방지
     * private 생성자로 외부에서 객체 생성 차단
     */
    private FileAttributesUtil() {
        // 유틸리티 클래스의 표준 패턴
        // 실수로라도 인스턴스 생성을 시도하면 예외 발생
        throw new UnsupportedOperationException("유틸리티 클래스는 인스턴스화할 수 없습니다");
    }

    /**
     * 파일의 기본 속성 읽기
     * Java NIO2의 Files.readAttributes()와 동일한 시그니처 제공
     *
     * @param path 파일 경로 (null 불허)
     * @param type 속성 타입 (현재는 BasicFileAttributes.class만 지원)
     * @param options 링크 옵션들 (현재는 무시됨, 향후 확장용)
     * @return BasicFileAttributes 구현체 객체
     * @throws IOException 파일 읽기 실패 시
     * @throws IllegalArgumentException 잘못된 매개변수
     * @throws UnsupportedOperationException 지원하지 않는 속성 타입
     */
    public static BasicFileAttributes readAttributes(Path path,
                                                     Class<BasicFileAttributes> type,
                                                     Object... options) throws IOException {
        // 1단계: 입력 검증 - 방어적 프로그래밍
        if (path == null) {
            throw new IllegalArgumentException("경로는 null일 수 없습니다");
        }

        if (type == null) {
            throw new IllegalArgumentException("속성 타입은 null일 수 없습니다");
        }

        // 현재는 BasicFileAttributes만 지원
        // 향후 PosixFileAttributes, DosFileAttributes 등 확장 가능
        if (!type.equals(BasicFileAttributes.class)) {
            throw new UnsupportedOperationException(
                    "현재 BasicFileAttributes만 지원됩니다. 요청된 타입: " + type.getName());
        }

        try {
            // 2단계: Path를 File로 변환
            // Path.toFile(): NIO2 Path를 레거시 File 객체로 변환
            File file = path.toFile();

            // 3단계: 파일 존재 여부 사전 확인
            // exists() 호출 이유:
            // 1. 명확한 에러 메시지 제공
            // 2. BasicFileAttributesImpl 내부에서 발생할 수 있는 혼란 방지
            // 3. 조기 실패로 불필요한 객체 생성 방지
            if (!file.exists()) {
                throw new IOException("파일이 존재하지 않습니다: " + path);
            }

            // 4단계: BasicFileAttributes 구현체 생성 및 반환
            return new BasicFileAttributesImpl(file);

        } catch (Exception e) {
            // 5단계: 예외 정규화
            // 모든 예외를 IOException으로 래핑하여 호출자가 처리하기 쉽게 함
            if (e instanceof IOException) {
                // 이미 IOException이면 그대로 재발생
                throw e;
            } else {
                // 다른 예외는 IOException으로 감싸서 발생
                // 원인 예외 정보는 cause로 보존
                throw new IOException("파일 속성 읽기 실패: " + path, e);
            }
        }
    }

    /**
     * 파일의 마지막 수정 시간만 빠르게 조회
     * 전체 속성 객체 생성 없이 수정 시간만 효율적으로 조회
     * HTTP Last-Modified 헤더 생성에 최적화됨
     *
     * @param path 파일 경로 (null 불허)
     * @return 마지막 수정 시간을 나타내는 FileTime 객체
     * @throws IOException 파일 읽기 실패 시
     * @throws IllegalArgumentException path가 null인 경우
     */
    public static FileTime getLastModifiedTime(Path path) throws IOException {
        // 입력 검증
        if (path == null) {
            throw new IllegalArgumentException("경로는 null일 수 없습니다");
        }

        try {
            // File 객체로 변환
            File file = path.toFile();

            // 파일 존재 확인 - 명확한 에러 메시지 제공
            if (!file.exists()) {
                throw new IOException("파일이 존재하지 않습니다: " + path);
            }

            // 마지막 수정 시간 조회
            // file.lastModified(): 에포크부터의 밀리초 반환
            long millis = file.lastModified();

            // 0 반환 시 처리 - 파일 시스템 오류나 권한 문제
            if (millis == 0) {
                throw new IOException("파일 수정 시간을 읽을 수 없습니다: " + path +
                        " (권한 부족이거나 파일 시스템 오류일 수 있습니다)");
            }

            // FileTime 객체로 변환하여 반환
            return FileTime.fromMillis(millis);

        } catch (Exception e) {
            // 예외 정규화 - IOException이 아닌 예외들 처리
            if (e instanceof IOException) {
                throw e;
            } else {
                throw new IOException("파일 수정 시간 조회 실패: " + path, e);
            }
        }
    }

    /**
     * 파일 크기만 빠르게 조회
     * 전체 속성 객체 생성 없이 크기만 효율적으로 조회
     * HTTP Content-Length 헤더와 Range 요청 처리에 최적화됨
     *
     * @param path 파일 경로 (null 불허)
     * @return 파일 크기 (바이트 단위, 0 이상)
     * @throws IOException 파일 읽기 실패 시
     * @throws IllegalArgumentException path가 null인 경우
     */
    public static long size(Path path) throws IOException {
        // 입력 검증
        if (path == null) {
            throw new IllegalArgumentException("경로는 null일 수 없습니다");
        }

        try {
            // File 객체로 변환
            File file = path.toFile();

            // 파일 존재 확인
            if (!file.exists()) {
                throw new IOException("파일이 존재하지 않습니다: " + path);
            }

            // 디렉토리인 경우 명시적 오류 - 크기 개념이 모호함
            if (file.isDirectory()) {
                throw new IOException("디렉토리의 크기는 조회할 수 없습니다: " + path +
                        " (디렉토리는 크기 개념이 없습니다)");
            }

            // 파일 크기 반환
            // file.length(): 파일 크기를 바이트 단위로 반환
            // 빈 파일의 경우 0 반환 (정상적인 상황)
            return file.length();

        } catch (Exception e) {
            // 예외 정규화
            if (e instanceof IOException) {
                throw e;
            } else {
                throw new IOException("파일 크기 조회 실패: " + path, e);
            }
        }
    }

    /**
     * 파일 존재 여부 확인
     * 예외를 발생시키지 않는 안전한 존재 여부 확인
     * 조건부 처리나 사전 검증에 유용
     *
     * @param path 파일 경로 (null 허용)
     * @return 파일이 존재하고 접근 가능하면 true, 그렇지 않으면 false
     */
    public static boolean exists(Path path) {
        try {
            // null 체크 - null 경로는 존재하지 않는 것으로 처리
            if (path == null) {
                return false;
            }

            // File 객체로 변환하여 존재 여부 확인
            // file.exists(): 파일이나 디렉토리가 존재하는지 확인
            return path.toFile().exists();

        } catch (Exception e) {
            // 모든 예외를 false로 처리 - 안전한 폴백
            // 예외 발생 상황:
            // 1. 경로 변환 오류
            // 2. 보안 예외 (SecurityException)
            // 3. 기타 시스템 오류
            //
            // 로깅하지 않는 이유: 이 메서드는 조건 확인용이므로 예외가 정상적 흐름
            return false;
        }
    }

    /**
     * 디렉토리 여부 확인
     * 예외를 발생시키지 않는 안전한 디렉토리 확인
     * 파일 타입 분류나 보안 검증에 유용
     *
     * @param path 파일 경로 (null 허용)
     * @return 디렉토리이면 true, 일반 파일이거나 존재하지 않으면 false
     */
    public static boolean isDirectory(Path path) {
        try {
            // null 체크 - null 경로는 디렉토리가 아님
            if (path == null) {
                return false;
            }

            // File 객체로 변환하여 디렉토리 여부 확인
            // file.isDirectory(): 경로가 디렉토리인지 확인
            // true 조건: 존재하고, 디렉토리이고, 접근 가능
            return path.toFile().isDirectory();

        } catch (Exception e) {
            // 모든 예외를 false로 처리 - 안전한 폴백
            // exists()와 동일한 정책 적용
            return false;
        }
    }

    /**
     * 일반 파일 여부 확인
     * 예외를 발생시키지 않는 안전한 일반 파일 확인
     * 정적 파일 서빙 가능 여부 판단에 유용
     *
     * @param path 파일 경로 (null 허용)
     * @return 일반 파일이면 true, 디렉토리이거나 존재하지 않으면 false
     */
    public static boolean isRegularFile(Path path) {
        try {
            // null 체크
            if (path == null) {
                return false;
            }

            // File 객체로 변환하여 일반 파일 여부 확인
            // file.isFile(): 경로가 일반 파일인지 확인
            // true 조건: 존재하고, 일반 파일이고, 접근 가능
            return path.toFile().isFile();

        } catch (Exception e) {
            // 안전한 폴백
            return false;
        }
    }

    /**
     * 파일의 읽기 가능 여부 확인
     * 정적 파일 서빙 전 권한 확인에 유용
     *
     * @param path 파일 경로 (null 허용)
     * @return 읽기 가능하면 true, 그렇지 않으면 false
     */
    public static boolean isReadable(Path path) {
        try {
            // null 체크
            if (path == null) {
                return false;
            }

            // File 객체로 변환하여 읽기 권한 확인
            // file.canRead(): 파일 읽기 권한이 있는지 확인
            File file = path.toFile();
            return file.exists() && file.canRead();

        } catch (Exception e) {
            // 안전한 폴백 - 권한 없음으로 처리
            return false;
        }
    }

    /**
     * 파일이 숨김 파일인지 확인
     * 시스템 파일이나 설정 파일 서빙 방지에 유용
     *
     * @param path 파일 경로 (null 허용)
     * @return 숨김 파일이면 true, 그렇지 않으면 false
     */
    public static boolean isHidden(Path path) {
        try {
            // null 체크
            if (path == null) {
                return false;
            }

            // File 객체로 변환하여 숨김 속성 확인
            // file.isHidden(): OS별 숨김 파일 규칙 적용
            // - Windows: 숨김 속성 비트 확인
            // - Unix/Linux: 파일명이 '.'으로 시작하는지 확인
            return path.toFile().isHidden();

        } catch (Exception e) {
            // 안전한 폴백 - 숨김 파일이 아닌 것으로 처리
            return false;
        }
    }

    /**
     * 두 경로가 같은 파일을 가리키는지 확인
     * 심볼릭 링크나 상대 경로 처리에 유용
     *
     * @param path1 첫 번째 경로 (null 허용)
     * @param path2 두 번째 경로 (null 허용)
     * @return 같은 파일을 가리키면 true, 그렇지 않으면 false
     */
    public static boolean isSameFile(Path path1, Path path2) {
        try {
            // null 체크 - 둘 다 null이면 같은 것으로 처리
            if (path1 == null && path2 == null) {
                return true;
            }

            // 하나만 null이면 다른 것으로 처리
            if (path1 == null || path2 == null) {
                return false;
            }

            // File 객체로 변환하여 비교
            // File.equals()는 정규화된 경로로 비교하므로 상대적으로 안전
            File file1 = path1.toFile();
            File file2 = path2.toFile();

            // 정규화된 절대 경로로 비교
            return file1.getCanonicalPath().equals(file2.getCanonicalPath());

        } catch (Exception e) {
            // 비교 불가능한 경우 다른 파일로 처리
            return false;
        }
    }
}