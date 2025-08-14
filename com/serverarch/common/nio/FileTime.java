package com.serverarch.common.nio;

// Java 시간 관련 라이브러리
// java.time.Instant: 특정 시점을 나타내는 클래스 - 에포크(1970년 1월 1일)부터의 시간
import java.time.Instant;

/**
 * FileTime 클래스
 * 파일 시스템의 시간 정보를 나타내는 불변 클래스
 *
 * 설계 특징:
 * 1. 불변 객체 - 생성 후 변경 불가로 스레드 안전성 보장
 * 2. 밀리초 정밀도 지원 - HTTP 날짜 형식과 호환
 * 3. Java 8 Time API와 상호 변환 가능 - 현대적 시간 처리
 * 4. Comparable 구현 - 시간 순서 비교 가능
 *
 * 사용 용도:
 * - HTTP Last-Modified 헤더 값 생성
 * - If-Modified-Since 조건부 요청 처리
 * - 파일 캐시 유효성 검증
 * - ETag 생성을 위한 시간 정보
 */
public final class FileTime implements Comparable<FileTime> {

    // 내부 시간 저장 - 에포크(1970-01-01T00:00:00Z)부터의 밀리초
    // final 키워드로 불변성 보장 - 생성 후 변경 불가능
    // private로 캡슐화 - 외부에서 직접 접근 불가
    private final long millis;

    /**
     * FileTime 생성자 (패키지 프라이빗)
     * 외부에서 직접 생성하지 못하도록 제한하고 정적 팩토리 메서드 사용 강제
     *
     * @param millis 에포크(1970년 1월 1일)부터의 밀리초
     */
    private FileTime(long millis) {
        // this.millis: 현재 객체의 millis 필드를 의미
        // 매개변수로 받은 millis 값을 내부 필드에 저장
        this.millis = millis;
    }

    /**
     * 밀리초 값으로 FileTime 생성
     * 가장 일반적인 FileTime 생성 방법
     *
     * @param value 에포크부터의 밀리초 (양수/음수 모두 가능)
     * @return 새로운 FileTime 객체
     */
    public static FileTime fromMillis(long value) {
        // 정적 팩토리 메서드 패턴 사용
        // 장점: 1) 의미있는 이름 제공, 2) 캐싱 가능, 3) 하위 타입 반환 가능
        return new FileTime(value);
    }

    /**
     * Instant 객체로 FileTime 생성
     * Java 8+ 시간 API와의 호환성 제공
     *
     * @param instant 시점을 나타내는 Instant 객체 (null 불허)
     * @return 새로운 FileTime 객체
     * @throws NullPointerException instant가 null인 경우
     */
    public static FileTime from(Instant instant) {
        // null 체크 - 방어적 프로그래밍으로 NPE 방지
        if (instant == null) {
            throw new NullPointerException("Instant는 null일 수 없습니다");
        }

        // instant.toEpochMilli(): Instant를 에포크부터의 밀리초로 변환
        // Java 8의 현대적인 시간 API와 호환성 제공
        return new FileTime(instant.toEpochMilli());
    }

    /**
     * 현재 시간으로 FileTime 생성
     * 파일이 생성된 시점이나 기본값으로 사용
     *
     * @return 현재 시간의 FileTime 객체
     */
    public static FileTime now() {
        // System.currentTimeMillis(): 현재 시간을 밀리초로 반환
        // 가장 성능이 좋은 현재 시간 조회 방법
        return new FileTime(System.currentTimeMillis());
    }

    /**
     * 밀리초 값 반환
     * 다른 시간 API나 HTTP 날짜 처리에서 사용
     *
     * @return 에포크부터의 밀리초 값
     */
    public long toMillis() {
        // 내부에 저장된 밀리초 값을 그대로 반환
        // 불변 객체이므로 안전하게 직접 반환 가능
        return millis;
    }

    /**
     * Instant 객체로 변환
     * Java 8 시간 API와의 상호 운용성 제공
     *
     * @return 동일한 시점을 나타내는 Instant 객체
     */
    public Instant toInstant() {
        // Instant.ofEpochMilli(): 밀리초 값으로 Instant 객체 생성
        // 변환 과정에서 정밀도 손실 없음 (밀리초 -> 밀리초)
        return Instant.ofEpochMilli(millis);
    }

    /**
     * 다른 FileTime과 시간 순서 비교
     * Collections.sort()나 우선순위 큐에서 사용 가능
     *
     * @param other 비교할 FileTime 객체 (null 불허)
     * @return 이 시간이 더 이르면 음수, 같으면 0, 더 늦으면 양수
     * @throws NullPointerException other가 null인 경우
     */
    @Override
    public int compareTo(FileTime other) {
        // null 체크 - Comparable 계약에 따라 NPE 발생
        if (other == null) {
            throw new NullPointerException("비교 대상 FileTime이 null입니다");
        }

        // Long.compare(): long 값들을 안전하게 비교하는 정적 메서드
        // 오버플로우 없이 정확한 비교 결과 반환 (-1, 0, 1)
        // this.millis < other.millis → 음수
        // this.millis == other.millis → 0
        // this.millis > other.millis → 양수
        return Long.compare(this.millis, other.millis);
    }

    /**
     * 두 FileTime 객체의 동등성 비교
     * HashMap, HashSet의 키로 사용할 때 필수
     *
     * @param obj 비교할 객체 (null 허용)
     * @return 같은 시간을 나타내면 true, 다르면 false
     */
    @Override
    public boolean equals(Object obj) {
        // 1단계: 동일 객체 참조 확인 - 가장 빠른 비교
        // == 연산자: 객체 참조 주소 비교
        if (this == obj) return true;

        // 2단계: null 체크 - NPE 방지
        if (obj == null) return false;

        // 3단계: 타입 확인 - ClassCastException 방지
        // instanceof: 객체가 특정 타입인지 확인하는 연산자
        if (!(obj instanceof FileTime)) return false;

        // 4단계: 형변환 후 값 비교
        FileTime other = (FileTime) obj;

        // 밀리초 값이 같으면 동일한 시간
        // long == long: 원시 타입 값 비교
        return this.millis == other.millis;
    }

    /**
     * 해시 코드 생성
     * HashMap, HashSet에서 객체를 빠르게 찾기 위해 사용
     * equals()가 true인 객체는 같은 해시코드를 가져야 함
     *
     * @return 해시 코드 값
     */
    @Override
    public int hashCode() {
        // Long.hashCode(): long 값의 해시 코드를 안전하게 생성
        // 내부적으로 (int)(value ^ (value >>> 32)) 계산 수행
        // 상위 32비트와 하위 32비트를 XOR하여 분산성 향상
        return Long.hashCode(millis);
    }

    /**
     * 사람이 읽기 쉬운 문자열 표현
     * 디버깅과 로깅에 유용
     *
     * @return ISO-8601 형식의 시간 문자열
     */
    @Override
    public String toString() {
        // Instant를 통해 표준 ISO-8601 형식으로 변환
        // 예시: "2024-01-15T10:30:45.123Z"
        // - T: 날짜와 시간 구분자
        // - Z: UTC 시간대 표시 (Zulu time)
        // - 밀리초까지 표시 (.123)
        return toInstant().toString();
    }

    /**
     * 두 FileTime 사이의 차이를 밀리초로 반환
     * 시간 간격 계산에 유용한 유틸리티 메서드
     *
     * @param other 비교할 FileTime (null 불허)
     * @return 시간 차이 (밀리초, 음수 가능)
     */
    public long minus(FileTime other) {
        // null 체크
        if (other == null) {
            throw new NullPointerException("비교 대상 FileTime이 null입니다");
        }

        // 단순 뺄셈 - 오버플로우 가능하지만 실제로는 매우 드문 상황
        return this.millis - other.millis;
    }

    /**
     * 지정된 밀리초만큼 이후의 시간을 반환
     * 캐시 만료 시간 계산 등에 사용
     *
     * @param millisToAdd 추가할 밀리초 (음수 가능)
     * @return 새로운 FileTime 객체
     */
    public FileTime plusMillis(long millisToAdd) {
        // 새로운 객체 생성 - 불변 객체 패턴 유지
        // 오버플로우 체크 없음 (실제 사용에서는 문제없음)
        return new FileTime(this.millis + millisToAdd);
    }

    /**
     * 지정된 밀리초만큼 이전의 시간을 반환
     *
     * @param millisToSubtract 뺄 밀리초 (음수 가능)
     * @return 새로운 FileTime 객체
     */
    public FileTime minusMillis(long millisToSubtract) {
        // plusMillis의 음수 버전 - 코드 일관성
        return plusMillis(-millisToSubtract);
    }
}