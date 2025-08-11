package jakarta.servlet.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * multipart/form-data 요청의 각 부분을 나타내는 인터페이스입니다.
 *
 * Part는 HTML 폼에서 파일 업로드나 복합 데이터 전송 시 사용되는
 * multipart/form-data 요청의 개별 부분을 추상화합니다.
 *
 * 각 Part는 다음을 포함할 수 있습니다:
 * - 텍스트 필드의 값
 * - 업로드된 파일의 내용
 * - 각 부분에 대한 헤더 정보
 * - 메타데이터 (파일명, 콘텐츠 타입 등)
 *
 * 사용 예시:
 * @MultipartConfig
 * public class FileUploadServlet extends HttpServlet {
 *     protected void doPost(HttpServletRequest request, HttpServletResponse response)
 *             throws ServletException, IOException {
 *
 *         Part filePart = request.getPart("file");
 *         String fileName = getSubmittedFileName(filePart);
 *         InputStream fileContent = filePart.getInputStream();
 *
 *         // 파일 처리 로직
 *     }
 * }
 */
public interface Part {

    /**
     * 이 Part의 입력 스트림을 반환합니다.
     *
     * Part의 내용을 읽기 위한 InputStream을 제공합니다.
     * 파일 업로드의 경우 파일 내용을, 텍스트 필드의 경우 텍스트 데이터를 읽을 수 있습니다.
     *
     * 주의사항:
     * - 스트림은 사용 후 반드시 닫아야 합니다
     * - 동일한 Part에서 getInputStream()을 여러 번 호출하면
     *   새로운 스트림을 반환하거나 예외를 발생시킬 수 있습니다
     * - 대용량 파일의 경우 메모리 사용량에 주의해야 합니다
     *
     * 사용 예시:
     * try (InputStream input = part.getInputStream()) {
     *     byte[] buffer = new byte[8192];
     *     int bytesRead;
     *     while ((bytesRead = input.read(buffer)) != -1) {
     *         // 데이터 처리
     *     }
     * }
     *
     * @return Part 내용의 InputStream
     * @throws IOException 입출력 오류 시
     */
    InputStream getInputStream() throws IOException;

    /**
     * 지정된 이름의 헤더 값을 반환합니다.
     *
     * 각 Part는 고유한 헤더를 가질 수 있습니다.
     * 가장 일반적인 헤더는 Content-Disposition과 Content-Type입니다.
     *
     * 일반적인 헤더들:
     * - "content-disposition": 필드명과 파일명 정보
     * - "content-type": 데이터의 MIME 타입
     * - "content-length": 데이터의 크기
     *
     * 예시:
     * Content-Disposition: form-data; name="file"; filename="document.pdf"
     * Content-Type: application/pdf
     *
     * @param name 헤더 이름 (대소문자 무관)
     * @return 헤더 값, 존재하지 않으면 null
     */
    String getHeader(String name);

    /**
     * 지정된 이름의 모든 헤더 값을 반환합니다.
     *
     * 동일한 이름의 헤더가 여러 개 있는 경우 모든 값을 반환합니다.
     *
     * @param name 헤더 이름
     * @return 해당 헤더의 모든 값들의 Collection
     */
    Collection<String> getHeaders(String name);

    /**
     * 모든 헤더의 이름을 반환합니다.
     *
     * 이 Part에 포함된 모든 헤더의 이름을 반환합니다.
     * 디버깅이나 헤더 분석에 유용합니다.
     *
     * @return 모든 헤더 이름들의 Collection
     */
    Collection<String> getHeaderNames();

    /**
     * 이 Part의 콘텐츠 타입을 반환합니다.
     *
     * Content-Type 헤더의 값을 반환합니다.
     * 파일의 경우 파일의 MIME 타입, 텍스트 필드의 경우 보통 "text/plain"입니다.
     *
     * 일반적인 콘텐츠 타입:
     * - "text/plain": 텍스트 필드
     * - "image/jpeg": JPEG 이미지
     * - "application/pdf": PDF 파일
     * - "application/octet-stream": 바이너리 파일 (타입 미지정)
     *
     * @return 콘텐츠 타입, 지정되지 않았으면 null
     */
    String getContentType();

    /**
     * 이 Part의 이름을 반환합니다.
     *
     * HTML 폼에서 input 요소의 name 속성에 해당합니다.
     * Content-Disposition 헤더에서 추출됩니다.
     *
     * 예시:
     * HTML: <input type="file" name="uploadFile">
     * 반환값: "uploadFile"
     *
     * @return Part의 이름
     */
    String getName();

    /**
     * 업로드된 파일의 원본 파일명을 반환합니다.
     *
     * 클라이언트가 업로드한 파일의 원래 이름을 반환합니다.
     * Content-Disposition 헤더의 filename 매개변수에서 추출됩니다.
     *
     * 주의사항:
     * - 파일명에는 경로 정보가 포함될 수 있음 (보안 위험)
     * - 클라이언트가 악의적인 파일명을 보낼 수 있음
     * - 파일명이 없는 경우 (텍스트 필드) null 반환
     * - 브라우저에 따라 전체 경로나 파일명만 전송할 수 있음
     *
     * 보안을 위해 파일명을 그대로 사용하지 말고 적절히 검증하고 정제해야 합니다.
     *
     * @return 원본 파일명, 파일이 아니거나 파일명이 없으면 null
     */
    String getSubmittedFileName();

    /**
     * 이 Part의 크기를 바이트 단위로 반환합니다.
     *
     * Part 내용의 전체 크기를 반환합니다.
     * 파일 업로드 시 파일 크기를 확인하거나 메모리 사용량을 계산할 때 유용합니다.
     *
     * @return Part의 크기 (바이트)
     */
    long getSize();

    /**
     * 이 Part를 지정된 파일에 저장합니다.
     *
     * Part의 내용을 파일 시스템의 지정된 위치에 저장합니다.
     * 업로드된 파일을 서버에 저장할 때 사용됩니다.
     *
     * 파일명은 절대 경로나 상대 경로일 수 있습니다.
     * 상대 경로의 경우 현재 작업 디렉토리를 기준으로 합니다.
     *
     * 보안 고려사항:
     * - 파일을 저장할 디렉토리가 웹 루트 외부에 있는지 확인
     * - 파일명에 "../" 같은 경로 조작 문자가 없는지 검증
     * - 디스크 공간이 충분한지 확인
     * - 파일 권한을 적절히 설정
     *
     * 사용 예시:
     * String uploadDir = "/var/uploads/";
     * String fileName = getSubmittedFileName(part);
     * String safeName = sanitizeFileName(fileName);
     * part.write(uploadDir + safeName);
     *
     * @param fileName 저장할 파일의 경로
     * @throws IOException 파일 쓰기 실패 시
     */
    void write(String fileName) throws IOException;

    /**
     * 이 Part를 삭제하거나 정리합니다.
     *
     * Part와 관련된 임시 파일이나 메모리를 정리합니다.
     * 서블릿 컨테이너가 임시 저장소를 사용하는 경우,
     * 이 메서드를 호출하여 리소스를 해제할 수 있습니다.
     *
     * 일반적으로 요청 처리가 완료된 후 자동으로 호출되지만,
     * 메모리나 디스크 공간이 부족한 상황에서는 명시적으로 호출할 수 있습니다.
     *
     * delete() 호출 후에는 이 Part의 다른 메서드를 호출하면
     * 예외가 발생할 수 있습니다.
     *
     * @throws IOException 삭제 실패 시
     */
    void delete() throws IOException;
}
