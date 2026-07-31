package org.lee.rocket.train.common.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 大文件流式下载 Servlet
 *
 * 【为什么不用 Spring MVC 的 ResponseEntity<Resource>？】
 * Spring MVC 的文件下载通常会先将文件加载到内存（或临时文件），再通过 HttpMessageConverter 转换。
 * 对于大文件（如几百 MB 的视频），这会导致：
 * 1. 内存占用过高，可能触发 OOM
 * 2. 响应延迟，因为要等文件完全加载后才开始传输
 *
 * 【原生 Servlet 的优势】
 * 直接操作 ServletOutputStream，实现"边读边写"：
 * - 从输入流读取一块数据（如 8KB）
 * - 立即写入输出流发送给客户端
 * - 继续读取下一块数据
 * 这样无论文件多大，内存占用始终保持在缓冲区大小（8KB），不会 OOM。
 *
 * 【应用场景】
 * - 大文件下载（视频、压缩包、数据库备份等）
 * - 实时生成的文件下载（如动态生成的报表）
 * - 文件代理（从远程服务器读取并转发给客户端）
 *
 * 【在请求链路中的位置】
 * 客户端请求 → Filter 链 → FileDownloadServlet（不经过 DispatcherServlet）
 *
 * 【注册方式】
 * 通过 ServletConfig.java 中的 ServletRegistrationBean 注册，URL 映射为 /download/*
 *
 * 【日志】
 * 使用 SLF4J（通过 Lombok @Slf4j 注入 log 字段）替代 System.out/err，便于统一日志级别与输出。
 */
@Slf4j
public class FileDownloadServlet extends HttpServlet {

    /**
     * 缓冲区大小：8KB
     *
     * 【为什么是 8KB？】
     * 这是 I/O 操作的经验值：
     * - 太小（如 1KB）：频繁的系统调用，CPU 开销大
     * - 太大（如 1MB）：内存占用高，且超过磁盘页大小后性能提升不明显
     * - 8KB = 2 个磁盘页（4KB），是大多数操作系统的推荐值
     *
     * 【大厂做法】
     * 阿里、美团等大厂通常使用 16KB 或 32KB，因为服务器磁盘 I/O 性能更好。
     * 但对于学习项目，8KB 是一个安全的起点。
     */
    private static final int BUFFER_SIZE = 8192;

    /**
     * 处理 GET 请求（文件下载）
     *
     * 【核心逻辑】
     * 1. 动态生成一个测试文件（约 1MB）
     * 2. 设置响应头，告诉浏览器这是一个文件下载
     * 3. 使用缓冲区流式传输，边读边写
     * 4. 在 finally 块中关闭流，防止资源泄漏
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        log.info("[FileDownloadServlet] 开始处理文件下载请求");

        // ===== 1. 准备文件数据 =====
        // 这里动态生成一个约 1MB 的测试数据
        // 实际场景中，这里可能是：
        // - new FileInputStream("/path/to/large-file.zip")
        // - ossClient.getObject(bucketName, key).getObjectContent()
        // - new URL("http://remote-server/file").openStream()
        InputStream inputStream = generateTestData();

        // ===== 2. 设置响应头 =====
        // 【重要】必须在获取 OutputStream 之前设置响应头，否则可能不生效

        // Content-Type: application/octet-stream
        // 告诉浏览器这是一个二进制文件，不要尝试解析，直接下载
        // 如果是特定类型（如 PDF），可以设置为 application/pdf
        response.setContentType("application/octet-stream");

        // Content-Disposition: attachment; filename="xxx"
        // attachment 表示以附件形式下载，而不是在浏览器中打开
        // filename 指定下载时的默认文件名
        // 【注意】文件名需要 URL 编码，否则中文会乱码
        String fileName = "test-data.txt";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");

        // Content-Length: 文件大小
        // 【可选但推荐】告诉浏览器文件大小，浏览器可以显示下载进度
        // 如果不知道大小（如动态生成的流），可以不设置，浏览器会显示"未知大小"
        // response.setContentLength(fileSize);

        // ===== 3. 流式传输 =====
        // 获取响应输出流
        // 【注意】OutputStream 由 Servlet 容器管理，不要手动关闭
        // 容器会在请求处理完成后自动关闭
        OutputStream outputStream = response.getOutputStream();

        // 缓冲区：用于临时存储读取的数据
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        long totalBytes = 0;

        try {
            // 循环读取输入流，直到结束（read() 返回 -1）
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                // 将读取的数据写入输出流
                // 【关键】这里只写入实际读取的字节数（bytesRead），而不是整个缓冲区
                // 否则会将缓冲区中的旧数据也写入，导致文件损坏
                outputStream.write(buffer, 0, bytesRead);

                // 累加已传输的字节数（用于日志统计）
                totalBytes += bytesRead;

                // 【可选】每传输一定量数据后 flush 一次
                // 确保数据及时发送给客户端，而不是堆积在缓冲区
                // 但如果 flush 太频繁，会降低性能
                // outputStream.flush();
            }

            // 所有数据写入完成后，flush 输出流
            // 确保缓冲区中的最后一块数据被发送
            outputStream.flush();

            log.info("[FileDownloadServlet] 文件传输完成，总大小: {} bytes", totalBytes);

        } finally {
            // ===== 4. 关闭流（防止资源泄漏） =====
            // 【重要】必须在 finally 块中关闭流，确保即使发生异常也能释放资源
            //
            // 【关闭顺序】先关闭输入流，再关闭输出流
            // 原因：输出流由 Servlet 容器管理，如果先关闭输出流，可能导致后续的错误信息无法写入响应
            //
            // 【为什么输出流可以不关闭？】
            // Servlet 容器的规范是：请求处理完成后，容器会自动关闭输出流
            // 但输入流必须手动关闭，否则会导致文件句柄泄漏
            //
            // 【try-with-resources vs finally】
            // Java 7+ 推荐使用 try-with-resources 自动管理资源
            // 但这里为了演示传统的 finally 写法，便于理解资源管理的本质
            if (inputStream != null) {
                try {
                    inputStream.close();
                    log.info("[FileDownloadServlet] 输入流已关闭");
                } catch (IOException e) {
                    // 关闭流时的异常通常无法恢复，只记录日志
                    log.error("[FileDownloadServlet] 关闭输入流失败: {}", e.getMessage());
                }
            }
            // 输出流不关闭，由 Servlet 容器管理
            // outputStream.close(); // 不要这样做
        }
    }

    /**
     * 生成测试数据（约 1MB）
     *
     * 【实际场景】
     * 这里为了演示，动态生成一个包含重复文本的输入流。
     * 实际项目中，这里应该是：
     * - 读取本地文件：new FileInputStream("/path/to/file")
     * - 读取 OSS 文件：ossClient.getObject(...).getObjectContent()
     * - 读取数据库 BLOB：resultSet.getBinaryStream("file_column")
     *
     * @return 输入流，包含测试数据
     */
    private InputStream generateTestData() {
        // 生成一行文本（约 100 字节）
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            line.append("This is a test line for file download. ");
        }
        line.append("\n");

        // 重复 10000 次，约 1MB
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            content.append("Line ").append(i + 1).append(": ").append(line);
        }

        // 转换为输入流
        return new java.io.ByteArrayInputStream(content.toString().getBytes(StandardCharsets.UTF_8));
    }
}
