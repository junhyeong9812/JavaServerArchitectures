# Java I/O 클래스들의 내부 동작 방식과 시스템 구동 방식

## 1. java.io.IOException

### 내부 동작 방식
```java
// IOException의 내부 구조 (간소화된 버전)
public class IOException extends Exception {
    // 직렬화를 위한 버전 ID
    private static final long serialVersionUID = 7818375828146090155L;
    
    // 기본 생성자 - 메시지 없는 예외
    public IOException() {
        super(); // Exception 클래스의 생성자 호출
    }
    
    // 메시지를 포함한 생성자
    public IOException(String message) {
        super(message); // Exception 클래스에 메시지 전달
    }
    
    // 원인 예외를 포함한 생성자 (예외 체이닝)
    public IOException(String message, Throwable cause) {
        super(message, cause); // 메시지와 원인 예외 모두 전달
    }
    
    // 원인 예외만 포함한 생성자
    public IOException(Throwable cause) {
        super(cause); // 원인 예외만 전달
    }
}
```

### 시스템 구동 방식
1. **예외 발생 시점**: 파일 읽기/쓰기, 네트워크 통신, 스트림 조작 중 I/O 오류 발생
2. **JVM 예외 처리 메커니즘**:
   ```
   I/O 작업 시도 → 시스템 콜 실행 → OS 레벨 오류 발생 → 
   JNI를 통해 Java로 오류 전달 → IOException 객체 생성 → 
   스택 트레이스 생성 → 예외 던지기 (throw)
   ```
3. **스택 언와인딩**: 예외가 발생하면 JVM이 호출 스택을 거슬러 올라가며 catch 블록 탐색
4. **메모리 관리**: 예외 객체는 힙 메모리에 생성되고, 가비지 컬렉터에 의해 관리됨

---

## 2. java.io.PrintWriter

### 내부 동작 방식
```java
// PrintWriter의 내부 구조 (핵심 부분)
public class PrintWriter extends Writer {
    // 내부 Writer 객체 - 실제 쓰기 작업을 담당
    protected Writer out;
    
    // 자동 플러시 여부
    private boolean autoFlush = false;
    
    // 에러 발생 여부 플래그
    private boolean trouble = false;
    
    // 포맷터 객체 - printf 스타일 출력용
    private Formatter formatter;
    
    // 문자열 출력 메서드
    public void print(String s) {
        if (s == null) {
            s = "null"; // null을 문자열 "null"로 변환
        }
        write(s); // 내부 write() 메서드 호출
    }
    
    // 실제 쓰기 메서드
    public void write(String s) {
        try {
            synchronized (lock) { // 동기화 블록 - 스레드 안전성 보장
                ensureOpen(); // 스트림이 열려있는지 확인
                out.write(s); // 내부 Writer에 실제 쓰기 작업 위임
                if (autoFlush && (s.indexOf('\n') >= 0)) {
                    out.flush(); // 자동 플러시 설정 시 개행 문자에서 플러시
                }
            }
        } catch (InterruptedIOException x) {
            Thread.currentThread().interrupt(); // 스레드 인터럽트 상태 복원
        } catch (IOException x) {
            trouble = true; // 에러 플래그 설정
        }
    }
    
    // 버퍼 비우기
    public void flush() {
        try {
            synchronized (lock) {
                ensureOpen();
                out.flush(); // 내부 버퍼의 모든 데이터를 목적지로 전송
            }
        } catch (IOException x) {
            trouble = true;
        }
    }
}
```

### 시스템 구동 방식
1. **계층적 구조**:
   ```
   PrintWriter → OutputStreamWriter → OutputStream → 시스템 콜
   ```
2. **버퍼링 메커니즘**:
    - 내부적으로 8192바이트 크기의 문자 배열 버퍼 사용
    - 버퍼가 가득 차거나 flush() 호출 시 실제 쓰기 수행
    - 성능 향상을 위해 시스템 콜 횟수 최소화
3. **문자 인코딩 처리**:
    - 내부적으로 Charset.encode() 사용하여 문자를 바이트로 변환
    - 플랫폼 기본 인코딩 또는 지정된 인코딩 사용
4. **동기화**: synchronized 블록으로 멀티스레드 환경에서 안전성 보장

---

## 3. java.io.OutputStreamWriter

### 내부 동작 방식
```java
// OutputStreamWriter의 내부 구조
public class OutputStreamWriter extends Writer {
    // 실제 바이트 스트림
    private final OutputStream out;
    
    // 문자 인코더 - 문자를 바이트로 변환
    private final StreamEncoder se;
    
    // 생성자
    public OutputStreamWriter(OutputStream out, String charsetName) 
            throws UnsupportedEncodingException {
        super(out); // Writer의 잠금 객체 설정
        if (charsetName == null) {
            throw new NullPointerException("charsetName");
        }
        this.out = out;
        try {
            // 지정된 문자셋으로 StreamEncoder 생성
            se = StreamEncoder.forOutputStreamWriter(out, this, charsetName);
        } catch (UnsupportedCharsetException uce) {
            throw new UnsupportedEncodingException(charsetName);
        }
    }
    
    // 문자 배열 쓰기
    public void write(char cbuf[], int off, int len) throws IOException {
        se.write(cbuf, off, len); // StreamEncoder에 위임
    }
    
    // 플러시
    public void flush() throws IOException {
        se.flush(); // StreamEncoder의 버퍼를 비우고 OutputStream으로 전송
    }
}

// StreamEncoder의 핵심 동작
class StreamEncoder extends Writer {
    // 문자 버퍼
    private char[] cb;
    
    // 바이트 버퍼
    private byte[] bb;
    
    // 문자셋 인코더
    private CharsetEncoder encoder;
    
    public void write(char cbuf[], int off, int len) throws IOException {
        synchronized (lock) {
            ensureOpen();
            if (len == 0) return;
            
            // 입력 문자들을 내부 버퍼에 복사
            implWrite(cbuf, off, len);
        }
    }
    
    private void implWrite(char cbuf[], int off, int len) throws IOException {
        // 문자를 바이트로 인코딩하는 과정
        CharBuffer cb = CharBuffer.wrap(cbuf, off, len);
        ByteBuffer bb = ByteBuffer.allocate(len * 4); // 최대 4바이트/문자
        
        CoderResult cr = encoder.encode(cb, bb, false);
        if (cr.isError()) {
            cr.throwException(); // 인코딩 오류 시 예외 발생
        }
        
        // 인코딩된 바이트를 OutputStream에 쓰기
        out.write(bb.array(), 0, bb.position());
    }
}
```

### 시스템 구동 방식
1. **문자-바이트 변환 과정**:
   ```
   char[] → CharBuffer → CharsetEncoder → ByteBuffer → byte[] → OutputStream
   ```
2. **인코딩 알고리즘**:
    - UTF-8: 1-4바이트 가변 길이 인코딩
    - UTF-16: 2-4바이트 가변 길이 인코딩
    - ISO-8859-1: 1바이트 고정 길이 인코딩
3. **버퍼 관리**:
    - 문자 버퍼와 바이트 버퍼를 각각 관리
    - 인코딩 효율성을 위해 적절한 버퍼 크기 유지
4. **에러 처리**: 인코딩 불가능한 문자 발견 시 대체 문자 사용 또는 예외 발생

---

## 4. java.io.UnsupportedEncodingException

### 내부 동작 방식
```java
// UnsupportedEncodingException의 내부 구조
public class UnsupportedEncodingException extends IOException {
    // 직렬화 버전 ID
    private static final long serialVersionUID = -4274276298326136670L;
    
    // 기본 생성자
    public UnsupportedEncodingException() {
        super(); // IOException의 생성자 호출
    }
    
    // 인코딩 이름을 포함한 생성자
    public UnsupportedEncodingException(String enc) {
        super(enc); // 지원하지 않는 인코딩 이름을 메시지로 전달
    }
}
```

### 시스템 구동 방식
1. **인코딩 검증 과정**:
   ```java
   // Charset 클래스의 내부 검증 로직
   public static Charset forName(String charsetName) {
       if (charsetName == null) {
           throw new IllegalArgumentException("Null charset name");
       }
       
       // 표준 문자셋 맵에서 검색
       Charset cs = standardProvider.charsetForName(charsetName);
       if (cs != null) return cs;
       
       // 확장 문자셋 프로바이더에서 검색
       for (CharsetProvider cp : providers()) {
           cs = cp.charsetForName(charsetName);
           if (cs != null) return cs;
       }
       
       // 모든 검색 실패 시 예외 발생
       throw new UnsupportedCharsetException(charsetName);
   }
   ```

2. **예외 발생 시나리오**:
    - 존재하지 않는 문자셋 이름 지정
    - 플랫폼에서 지원하지 않는 문자셋 사용
    - 잘못된 문자셋 이름 형식

3. **시스템 레벨 지원**:
    - JVM이 시작할 때 사용 가능한 문자셋 목록 로드
    - 네이티브 라이브러리와 연동하여 OS 레벨 문자셋 지원 확인

---

## 전체 시스템 통합 동작 방식

### 1. HTTP 응답 출력 과정
```
HttpServlet.getWriter() 호출
↓
ServletResponse.getWriter() 구현체 실행
↓
OutputStreamWriter 생성 (지정된 문자 인코딩으로)
↓
PrintWriter로 래핑
↓
문자열 출력 시:
  PrintWriter.print() → OutputStreamWriter.write() → 
  StreamEncoder.encode() → ServletOutputStream.write() →
  HTTP 응답 스트림으로 전송
```

### 2. 에러 처리 흐름
```
인코딩 오류 발생
↓
UnsupportedEncodingException 생성
↓
IOException으로 상위 변환 (필요시)
↓
서블릿 컨테이너의 예외 처리기로 전달
↓
HTTP 500 에러 응답 생성
```

### 3. 메모리 및 리소스 관리
- **버퍼 풀링**: 재사용 가능한 버퍼 객체들을 풀에서 관리
- **가비지 컬렉션**: 사용 완료된 스트림 객체들의 자동 메모리 해제
- **네이티브 리소스**: JNI를 통한 OS 레벨 파일 디스크립터 관리
- **스레드 안전성**: synchronized 블록을 통한 동시성 제어

이러한 복잡한 내부 동작들이 모두 투명하게 처리되어, 개발자는 단순한 API만으로도 강력한 I/O 기능을 사용할 수 있습니다.