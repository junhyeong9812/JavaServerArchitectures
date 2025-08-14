package jakarta.servlet;

import java.io.IOException;

/**
 * 비동기 쓰기 작업의 이벤트를 처리하는 리스너 인터페이스입니다.
 *
 * Servlet 3.1의 논블로킹 I/O 기능에서 사용됩니다.
 */
public interface WriteListener {
    /**
     * 데이터 쓰기가 가능할 때 호출됩니다.
     */
    void onWritePossible() throws IOException;

    /**
     * 쓰기 중 오류가 발생했을 때 호출됩니다.
     */
    void onError(Throwable t);
}
