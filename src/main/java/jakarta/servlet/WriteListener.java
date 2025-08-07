package src.main.java.jakarta.servlet;

// Jakarta Servlet API - ReadListener & WriteListener Interfaces
// 실제 프로젝트에서는 각각 별도 파일로 분리

import java.io.IOException;
import java.util.EventListener;

/**
 * 논블로킹 쓰기 이벤트를 처리하는 리스너 인터페이스입니다.
 *
 * ServletOutputStream의 논블로킹 모드에서 사용되며,
 * 데이터를 쓸 수 있게 되었을 때나 에러가 발생했을 때 알림을 받습니다.
 *
 * @author JavaServerArchitectures Project
 * @version 1.0
 * @since Servlet 3.1
 * @see ServletOutputStream#setWriteListener(WriteListener)
 */
interface WriteListener extends EventListener {

    /**
     * 데이터를 쓸 수 있을 때 호출됩니다.
     *
     * 이 메소드 내에서 ServletOutputStream.isReady()를 확인하여
     * 블로킹 없이 쓸 수 있는 동안 데이터를 계속 써야 합니다.
     *
     * @throws IOException I/O 오류가 발생한 경우
     */
    void onWritePossible() throws IOException;

    /**
     * 에러가 발생했을 때 호출됩니다.
     *
     * @param t 발생한 예외
     */
    void onError(Throwable t);
}