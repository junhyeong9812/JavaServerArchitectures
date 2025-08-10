package src.main.java.jakarta.servlet;

import java.io.IOException;

/**
 * 비동기 읽기 작업의 이벤트를 처리하는 리스너 인터페이스입니다.
 *
 * Servlet 3.1의 논블로킹 I/O 기능에서 사용됩니다.
 */
interface ReadListener {
    /**
     * 데이터 읽기가 가능할 때 호출됩니다.
     */
    void onDataAvailable() throws IOException;

    /**
     * 모든 데이터를 읽었을 때 호출됩니다.
     */
    void onAllDataRead() throws IOException;

    /**
     * 읽기 중 오류가 발생했을 때 호출됩니다.
     */
    void onError(Throwable t);
}
