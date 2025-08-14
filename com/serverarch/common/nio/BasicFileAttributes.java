package com.serverarch.common.nio;

/**
 * BasicFileAttributes 인터페이스
 * Java NIO2의 파일 속성 시스템을 직접 구현
 *
 * 설계 목표:
 * 1. 표준 Java NIO2 API와 호환성 유지 - 기존 코드와 호환 가능
 * 2. 파일의 기본 메타데이터 정보 제공 - 크기, 수정시간, 타입 등
 * 3. 크로스 플랫폼 지원 (Windows, Linux, macOS) - 모든 OS에서 동일한 API
 * 4. 성능 최적화된 파일 속성 접근 - 필요한 속성만 읽기
 *
 * 사용 목적:
 * - StaticFileHandler에서 파일 메타데이터 조회
 * - HTTP 캐싱 헤더 생성 (Last-Modified, ETag)
 * - 파일 타입 확인 및 보안 검증
 */
public interface BasicFileAttributes {

    /**
     * 파일의 마지막 수정 시간 반환
     * HTTP Last-Modified 헤더와 캐시 검증에 사용
     *
     * @return FileTime 객체 - 마지막 수정 시간
     */
    FileTime lastModifiedTime();

    /**
     * 파일의 마지막 접근 시간 반환
     * 일부 파일 시스템에서는 지원하지 않을 수 있음
     *
     * @return FileTime 객체 - 마지막 접근 시간
     */
    FileTime lastAccessTime();

    /**
     * 파일의 생성 시간 반환
     * 일부 파일 시스템에서는 지원하지 않을 수 있음
     *
     * @return FileTime 객체 - 생성 시간
     */
    FileTime creationTime();

    /**
     * 일반 파일인지 확인
     * 정적 파일 서빙에서 서빙 가능한 파일인지 판단
     *
     * @return 일반 파일이면 true, 디렉토리나 특수 파일이면 false
     */
    boolean isRegularFile();

    /**
     * 디렉토리인지 확인
     * 디렉토리 리스팅이나 보안 검증에 사용
     *
     * @return 디렉토리이면 true, 일반 파일이면 false
     */
    boolean isDirectory();

    /**
     * 심볼릭 링크인지 확인
     * 보안상 심볼릭 링크 처리를 제한할 때 사용
     *
     * @return 심볼릭 링크이면 true, 일반 파일/디렉토리이면 false
     */
    boolean isSymbolicLink();

    /**
     * 기타 파일 타입인지 확인 (장치 파일, 파이프 등)
     * 특수 파일은 일반적으로 웹 서버에서 서빙하지 않음
     *
     * @return 특수 파일이면 true, 일반 파일/디렉토리/링크이면 false
     */
    boolean isOther();

    /**
     * 파일 크기 반환 (바이트 단위)
     * HTTP Content-Length 헤더와 Range 요청 처리에 사용
     *
     * @return 파일 크기 (바이트)
     */
    long size();

    /**
     * 파일 키 반환 (고유 식별자)
     * 파일 시스템에서 파일을 고유하게 식별하는 값
     * 캐싱이나 중복 확인에 사용할 수 있음
     *
     * @return 파일 시스템에서의 고유 키 (null일 수 있음)
     */
    Object fileKey();
}