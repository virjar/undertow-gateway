package io.undertow.examples.servlet;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

import static io.undertow.servlet.Servlets.*;

/**
 * 测试压缩功能的简单 HTTP 服务器。
 * 启动后用 curl -H "Accept-Encoding: gzip" --compressed http://localhost:18080/test 验证。
 */
public class CompressionTestServer {

    public static void main(String[] args) throws Exception {
        DeploymentInfo servletBuilder = deployment()
                .setClassLoader(CompressionTestServer.class.getClassLoader())
                .setContextPath("/")
                .setDeploymentName("compression-test.war")
                .addServlet(
                        servlet("TestServlet", TestServlet.class)
                                .addMapping("/test")
                );

        DeploymentManager manager = defaultContainer().addDeployment(servletBuilder);
        manager.deploy();

        HttpHandler servletHandler = manager.start();
        PathHandler path = Handlers.path().addPrefixPath("/", servletHandler);

        // 包装 EncodingHandler 启用 gzip 压缩
        ContentEncodingRepository repository = new ContentEncodingRepository();
        repository.addEncodingHandler("gzip", new GzipEncodingProvider(), 50);
        HttpHandler compressedHandler = new EncodingHandler(repository).setNext(path);

        Undertow server = Undertow.builder()
                .addHttpListener(18080, "0.0.0.0")
                .setHandler(compressedHandler)
                .build();
        server.start();
        System.out.println("Server started at http://localhost:18080/test");
        System.out.println("Test with: curl -v -H \"Accept-Encoding: gzip\" --compressed http://localhost:18080/test");
    }

    public static class TestServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("text/plain;charset=UTF-8");
            PrintWriter writer = resp.getWriter();
            // 写入足够大的内容，确保压缩有效果
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 200; i++) {
                sb.append("Hello, this is a test response for compression testing. Line ").append(i).append("\n");
            }
            writer.write(sb.toString());
        }
    }
}
