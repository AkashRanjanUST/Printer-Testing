package com.costco.replatform.foundation.printrouter.service;

import com.costco.replatform.foundation.printrouter.model.ApiResponse;
import com.costco.replatform.foundation.printrouter.model.PrintServerDiscoveryResponse;
import com.costco.replatform.foundation.printrouter.util.PrintRouterIppRequestBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class PrintServerDiscoveryServiceTest {

    private static final String PRINTER_PATH = "/printers/TestPrinter/.printer";

    /** What the fake print server received. */
    private record RecordedRequest(String method, String path, String contentType,
                                   String authorization, byte[] body) {
    }

    /** How the fake print server should answer. */
    @FunctionalInterface
    private interface Responder {
        void respond(HttpExchange exchange) throws IOException;
    }

    private HttpServer server;
    private String baseUrl;
    private volatile Responder responder;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

    private PrintServerDiscoveryService service;

    @BeforeEach
    void setUp() throws IOException {
        // A real HTTP server on 127.0.0.1
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            requests.add(new RecordedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    body));
            responder.respond(exchange);
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        service = new PrintServerDiscoveryService();
        ReflectionTestUtils.setField(service, "targetUrl", baseUrl);
        ReflectionTestUtils.setField(service, "printerPath", PRINTER_PATH);
        ReflectionTestUtils.setField(service, "printerName", "TestPrinter");
        ReflectionTestUtils.setField(service, "authUsername", "TEST\\test-user");
        ReflectionTestUtils.setField(service, "authPassword", "test-password");
        ReflectionTestUtils.setField(service, "authDomain", "TEST");
        ReflectionTestUtils.setField(service, "connectTimeout", 2000);
        ReflectionTestUtils.setField(service, "readTimeout", 2000);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        // The service sets a JVM-wide Authenticator; reset it so other tests are unaffected.
        Authenticator.setDefault(null);
    }

    /** Replies with a status and body; an empty body is sent as "no body". */
    private static Responder reply(int status, byte[] body) {
        return exchange -> {
            if (body.length == 0) {
                exchange.sendResponseHeaders(status, -1);
            } else {
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            }
            exchange.close();
        };
    }

    private static byte[] text(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private PrintServerDiscoveryResponse discover() {
        ApiResponse<PrintServerDiscoveryResponse> result = service.sendDiscoveryRequest();
        assertThat(result.isSuccess()).isTrue();
        return result.getResponse();
    }

    // 1. Response status branches

    @Test
    @DisplayName("200 with IPP body -> SUCCESS; request is a POST of the Get-Printer-Attributes payload")
    void ok200_success_andRequestIsCorrect() throws Exception {
        responder = reply(200, new byte[]{0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01});

        PrintServerDiscoveryResponse response = discover();

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getMessage()).isEqualTo("IPP request transmitted successfully");
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getUrl()).isEqualTo(baseUrl + PRINTER_PATH);
        assertThat(response.getErrorType()).isNull();

        assertThat(requests).hasSize(1);
        RecordedRequest request = requests.get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo(PRINTER_PATH);
        assertThat(request.contentType()).isEqualTo("application/ipp");

        String expectedIppUri = baseUrl.replace("http://", "ipp://") + PRINTER_PATH;
        assertThat(request.body())
                .isEqualTo(PrintRouterIppRequestBuilder.createGetPrinterAttributesRequest(1, expectedIppUri));
    }

    @Test
    @DisplayName("201 with empty body -> SUCCESS ('Empty' branch when logging first byte)")
    void created201_emptyBody_success() {
        responder = reply(201, new byte[0]);

        PrintServerDiscoveryResponse response = discover();

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getHttpStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("500 -> FAILED / SERVER_ERROR, message is the error body (read from error stream)")
    void serverError500_failed() {
        responder = reply(500, text("Internal printer failure"));

        PrintServerDiscoveryResponse response = discover();

        assertThat(response.getStatus()).isEqualTo("FAILED");
        assertThat(response.getErrorType()).isEqualTo("SERVER_ERROR");
        assertThat(response.getMessage()).isEqualTo("Internal printer failure");
        assertThat(response.getHttpStatus()).isEqualTo(500);
    }

    @Test
    @DisplayName("404 (other error code) -> PARTIAL_SUCCESS with the status in the message")
    void notFound404_partialSuccess() {
        responder = reply(404, text("no such printer"));

        PrintServerDiscoveryResponse response = discover();

        assertThat(response.getStatus()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(response.getMessage()).isEqualTo("Server returned status code: 404");
        assertThat(response.getHttpStatus()).isEqualTo(404);
        assertThat(response.getErrorType()).isNull();
    }

    @Test
    @DisplayName("202 (non-error, not 200/201) -> PARTIAL_SUCCESS, info branch")
    void accepted202_partialSuccess() {
        responder = reply(202, new byte[0]);

        PrintServerDiscoveryResponse response = discover();

        assertThat(response.getStatus()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(response.getMessage()).isEqualTo("Server returned status code: 202");
    }

    // 2. Authentication

    @Test
    @DisplayName("401 Basic challenge -> JDK calls our Authenticator, retries with configured credentials")
    void basicChallenge_usesConfiguredCredentials() {
        responder = exchange -> {
            if (exchange.getRequestHeaders().getFirst("Authorization") == null) {
                exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"print\"");
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
            } else {
                reply(200, new byte[]{0x01}).respond(exchange);
            }
        };

        PrintServerDiscoveryResponse response = discover();

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(requests).hasSize(2); // challenge, then authenticated retry
        String expected = "Basic " + Base64.getEncoder()
                .encodeToString(text("TEST\\test-user:test-password"));
        assertThat(requests.get(1).authorization()).isEqualTo(expected);
    }

    // 3. Failures are swallowed

    @Test
    @DisplayName("CURRENT BEHAVIOUR: invalid URL -> exception swallowed, ok(null) returned")
    void invalidUrl_returnsOkWithNull() {
        ReflectionTestUtils.setField(service, "targetUrl", "not-a-url");

        ApiResponse<PrintServerDiscoveryResponse> result = service.sendDiscoveryRequest();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResponse()).isNull();
        assertThat(requests).isEmpty();
    }

    @Test
    @DisplayName("CURRENT BEHAVIOUR: connection refused -> exception swallowed, ok(null) returned")
    void connectionRefused_returnsOkWithNull() {
        server.stop(0); // nothing is listening on the port any more

        ApiResponse<PrintServerDiscoveryResponse> result = service.sendDiscoveryRequest();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResponse()).isNull();
    }

    // 4. URI conversion (https branch cannot be reached with a plain HTTP test server)

    @Test
    @DisplayName("buildIppUri: http -> ipp, https -> ipps")
    void buildIppUri_convertsSchemes() throws Exception {
        Method method = PrintServerDiscoveryService.class.getDeclaredMethod("buildIppUri", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "http://host/printers/x")).isEqualTo("ipp://host/printers/x");
        assertThat(method.invoke(service, "https://host/printers/x")).isEqualTo("ipps://host/printers/x");
    }
}