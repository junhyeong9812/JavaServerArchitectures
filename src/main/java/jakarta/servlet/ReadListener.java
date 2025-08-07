package src.main.java.jakarta.servlet;

// Jakarta Servlet API - ReadListener & WriteListener Interfaces
// 실제 프로젝트에서는 각각 별도 파일로 분리

import java.io.IOException;
import java.util.EventListener;

/**
 * 논블로킹 읽기 이벤트를 처리하는 리스너 인터페이스입니다.
 *
 * ServletInputStream의 논블로킹 모드에서 사용되며,
 * 읽을 수 있는 데이터가 있거나 모든 데이터를 읽었을 때,
 * 또는 에러가 발생했을 때 알림을 받습니다.
 *
 * @author JavaServerArchitectures Project
 * @version 1.0
 * @since Servlet 3.1
 * @see ServletInputStream#setReadListener(ReadListener)
 */
interface ReadListener extends EventListener {

    /**
     * 읽을 수 있는 데이터가 있을 때 호출됩니다.
     *
     * 이 메소드 내에서 ServletInputStream.isReady()를 확인하여
     * 블로킹 없이 읽을 수 있는 동안 데이터를 계속 읽어야 합니다.
     *
     * @throws IOException I/O 오류가 발생한 경우
     */
    void onDataAvailable() throws IOException;

    /**
     * 모든 데이터를 읽었을 때 호출됩니다.
     *
     * 더 이상 읽을 데이터가 없음을 알리는 콜백입니다.
     *
     * @throws IOException I/O 오류가 발생한 경우
     */
    void onAllDataRead() throws IOException;

    /**
     * 에러가 발생했을 때 호출됩니다.
     *
     * @param t 발생한 예외
     */
    void onError(Throwable t);
}

