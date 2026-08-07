package com.github.martinfrancois;

import com.github.martinfrancois.testsupport.CapturedConsole;
import com.github.martinfrancois.testsupport.UtilityClasses;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link RequestHandler} against a real TCP server.
 *
 * <p>The server is the JDK's own {@code com.sun.net.httpserver.HttpServer} bound to a real
 * loopback socket, so every test drives the genuine {@code java.net.http.HttpClient}
 * request/response path including socket setup, HTTP framing and charset decoding.
 * There is no fake transport anywhere: if the HTTP stack stopped working, these fail.
 */
class RequestHandlerTest {

    private HttpServer server;
    private CapturedConsole console;

    /** Records what the server actually received, so we can assert on the wire behaviour. */
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        console = new CapturedConsole();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        console.close();
    }

    private String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    private void respondWith(String path, int status, String body) {
        server.createContext(path, recordingHandler(status, body));
    }

    private HttpHandler recordingHandler(int status, String body) {
        return (HttpExchange exchange) -> {
            lastMethod.set(exchange.getRequestMethod());
            lastPath.set(exchange.getRequestURI().getPath());
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        };
    }

    @Test
    void returnsTheResponseBodyOnHttp200() {
        String body = "[{\"title\":\"Google\",\"url\":\"https://www.google.com\"}]";
        respondWith("/json/list", 200, body);

        String result = RequestHandler.get(baseUrl() + "/json/list");

        assertThat(result).isEqualTo(body);
        assertThat(console.errors()).isEmpty();
    }

    @Test
    void issuesAGetRequestToTheExactPath() {
        respondWith("/json/list", 200, "[]");

        RequestHandler.get(baseUrl() + "/json/list");

        assertThat(lastMethod.get()).isEqualTo("GET");
        assertThat(lastPath.get()).isEqualTo("/json/list");
    }

    @Test
    void decodesTheResponseBodyAsUtf8() {
        // Multi-byte characters prove the body is decoded with the declared UTF-8 charset
        // rather than the platform default.
        String body = "[{\"title\":\"Ümlaut – café 😀 日本語\",\"url\":\"https://example.com\"}]";
        respondWith("/json/list", 200, body);

        assertThat(RequestHandler.get(baseUrl() + "/json/list")).isEqualTo(body);
    }

    @Test
    void readsALargeResponseBodyCompletely() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 20_000; i++) {
            builder.append("{\"title\":\"Tab ").append(i).append("\"},");
        }
        String body = builder.toString();
        respondWith("/json/list", 200, body);

        String result = RequestHandler.get(baseUrl() + "/json/list");

        assertThat(result).hasSize(body.length()).isEqualTo(body);
    }

    @Test
    void returnsAnEmptyStringWhenTheDeviceReportsNoTabs() {
        respondWith("/json/list", 200, "");

        assertThat(RequestHandler.get(baseUrl() + "/json/list")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 500, 503})
    void reportsNonSuccessStatusCodesAndReturnsNull(int status) {
        respondWith("/json/list", status, "nope");

        String result = RequestHandler.get(baseUrl() + "/json/list");

        assertThat(result).isNull();
        assertThat(console.errors())
                .containsExactly("HTTP Request failed with status code: " + status);
    }

    @Test
    void reportsAConnectionFailureWithTheTroubleshootingMessageAndRethrows() throws IOException {
        // Grab a port, then release it so nothing is listening: the real socket connect fails.
        int deadPort;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            deadPort = socket.getLocalPort();
        }
        String url = "http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + deadPort + "/json/list";

        assertThatThrownBy(() -> RequestHandler.get(url))
                .isInstanceOf(RuntimeException.class)
                .cause()
                .isInstanceOf(IOException.class);

        assertThat(console.anyErrorContains("Failed to connect to the phone")).isTrue();
        assertThat(console.anyErrorContains("USB debugging is enabled")).isTrue();
    }

    @Test
    void reportsAConnectionFailureWhenTheHostCannotBeResolved() {
        String url = "http://this-host-does-not-exist.invalid/json/list";

        assertThatThrownBy(() -> RequestHandler.get(url)).isInstanceOf(RuntimeException.class);

        assertThat(console.anyErrorContains("Failed to connect to the phone")).isTrue();
    }

    @Test
    void utilityClassCannotBeInstantiated() throws Exception {
        Throwable thrown = UtilityClasses.invokeConstructorAndCaptureThrowable(RequestHandler.class);

        assertThat(thrown)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("utility class");
    }
}
