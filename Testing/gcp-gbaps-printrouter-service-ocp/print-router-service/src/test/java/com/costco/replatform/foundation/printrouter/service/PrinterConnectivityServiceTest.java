package com.costco.replatform.foundation.printrouter.service;

import com.costco.replatform.foundation.printrouter.config.PrinterIppProperties;
import com.costco.replatform.foundation.printrouter.model.ApiResponse;
import com.costco.replatform.foundation.printrouter.model.PrinterIppResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrinterConnectivityServiceTest {

    private static final String QUEUE_URL = "http://print-server.test.invalid/printers/";
    private static final int ITEM_NO = 7;
    private static final String PRINTER_NAME = "Laser1";
    private static final String JOB_NAME = "JOB";

    private PrinterIppProperties properties;
    private HttpClient httpClient;
    private PrinterConnectivityService service;

    @BeforeEach
    void setUp() {
        properties = validProperties();
        service = new PrinterConnectivityService(properties);   // builds a real HttpClient...
        httpClient = mock(HttpClient.class);
        ReflectionTestUtils.setField(service, "httpClient", httpClient); // ...which we swap for a mock
    }

    // Helpers

    private static PrinterIppProperties validProperties() {
        PrinterIppProperties p = new PrinterIppProperties();
        p.setQueueUrl(QUEUE_URL);
        p.setRequestingUser("test-user");
        p.setDomainUsername("TEST\\test-user");
        p.setPassword("test-password");
        p.setJobName("TEST_JOB_");
        p.setPrintContent("Unit test print content");
        p.setConnectTimeoutSeconds(1);
        p.setRequestTimeoutSeconds(1);
        return p;
    }

    /** An 8-byte IPP response header: version 1.1, status, request id. */
    private static byte[] ippBody(int ippStatus, int requestId) {
        return ByteBuffer.allocate(8)
                .put((byte) 1).put((byte) 1)
                .putShort((short) ippStatus)
                .putInt(requestId)
                .array();
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<byte[]> httpResponse(int status, byte[] body) {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (name, value) -> true));
        return response;
    }

    private void printServerReturns(HttpResponse<byte[]> response) throws Exception {
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
    }

    private ApiResponse<PrinterIppResponse> send() {
        return service.sendPrintRequest(ITEM_NO, PRINTER_NAME, JOB_NAME);
    }

    private static void assertFailure(ApiResponse<PrinterIppResponse> result,
                                      int expectedStatus, String expectedMessage) {
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getResponse()).isNull();
        assertThat(result.getErrorCode()).isEqualTo("ERR_001");
        assertThat(result.getErrorStatus()).isEqualTo(String.valueOf(expectedStatus));
        assertThat(result.getMessage()).isEqualTo(expectedMessage);
    }

    // 1. validateInput() - every check, plus the HTTPS happy path

    static Stream<Arguments> invalidConfigurations() {
        return Stream.of(
                arguments("queue URL null", cfg(p -> p.setQueueUrl(null)), "Queue URL must not be empty"),
                arguments("queue URL blank", cfg(p -> p.setQueueUrl("   ")), "Queue URL must not be empty"),
                arguments("queue URL unparseable", cfg(p -> p.setQueueUrl("http://bad host/printers/")), "Queue URL is invalid"),
                arguments("queue URL ftp scheme", cfg(p -> p.setQueueUrl("ftp://print-server.test.invalid/printers/")), "Queue URL must use HTTP or HTTPS"),
                arguments("queue URL without scheme", cfg(p -> p.setQueueUrl("print-server.test.invalid/printers/")), "Queue URL must use HTTP or HTTPS"),
                arguments("queue URL without host", cfg(p -> p.setQueueUrl("http:///printers/")), "Queue URL must contain a valid host"),
                arguments("queue URL with empty path", cfg(p -> p.setQueueUrl("http://print-server.test.invalid")), "Queue URL must contain an IPP queue path"),
                arguments("queue URL with root path", cfg(p -> p.setQueueUrl("http://print-server.test.invalid/")), "Queue URL must contain an IPP queue path"),
                arguments("requesting user null", cfg(p -> p.setRequestingUser(null)), "Requesting user must not be empty"),
                arguments("requesting user blank", cfg(p -> p.setRequestingUser(" ")), "Requesting user must not be empty"),
                arguments("domain username null", cfg(p -> p.setDomainUsername(null)), "Authentication username must not be empty"),
                arguments("password null", cfg(p -> p.setPassword(null)), "Authentication password must not be empty"),
                arguments("job name null", cfg(p -> p.setJobName(null)), "Job name must not be empty"),
                arguments("print content null", cfg(p -> p.setPrintContent(null)), "Print content must not be empty"),
                arguments("connect timeout 0", cfg(p -> p.setConnectTimeoutSeconds(0)), "Connect timeout must be greater than zero"),
                arguments("connect timeout negative", cfg(p -> p.setConnectTimeoutSeconds(-1)), "Connect timeout must be greater than zero"),
                arguments("request timeout 0", cfg(p -> p.setRequestTimeoutSeconds(0)), "Request timeout must be greater than zero")
        );
    }

    /** Helps the compiler infer the lambda type inside arguments(...). */
    private static Consumer<PrinterIppProperties> cfg(Consumer<PrinterIppProperties> change) {
        return change;
    }

    @ParameterizedTest(name = "invalid config: {0}")
    @MethodSource("invalidConfigurations")
    void invalidConfiguration_returns400_andNeverCallsPrinter(
            String description, Consumer<PrinterIppProperties> breakConfig, String expectedMessage) throws Exception {
        breakConfig.accept(properties); // service holds this same object, so it sees the change

        ApiResponse<PrinterIppResponse> result = send();

        assertFailure(result, 400, expectedMessage);
        verify(httpClient, never()).send(any(), any());
    }

    @Test
    @DisplayName("HTTPS queue URL passes validation")
    void httpsQueueUrl_isAccepted() throws Exception {
        properties.setQueueUrl("https://print-server.test.invalid/printers/");
        printServerReturns(httpResponse(200, ippBody(0x0000, ITEM_NO)));

        assertThat(send().isSuccess()).isTrue();
    }

    // 2. processResponse() - HTTP status branches

    @Test
    @DisplayName("401 with WWW-Authenticate header -> scheme included, 401 returned")
    void http401_withAuthHeader() throws Exception {
        HttpResponse<byte[]> response = httpResponse(401, null);
        when(response.headers()).thenReturn(
                HttpHeaders.of(Map.of("WWW-Authenticate", List.of("NTLM")), (name, value) -> true));
        printServerReturns(response);

        assertFailure(send(), 401, "HTTP authentication failed. Scheme: NTLM");
    }

    @Test
    @DisplayName("401 without WWW-Authenticate header -> 'Not provided'")
    void http401_withoutAuthHeader() throws Exception {
        printServerReturns(httpResponse(401, null));

        assertFailure(send(), 401, "HTTP authentication failed. Scheme: Not provided");
    }

    @Test
    @DisplayName("403 -> not authorized for this queue")
    void http403() throws Exception {
        printServerReturns(httpResponse(403, null));

        assertFailure(send(), 403, "User is not authorized to print to this queue");
    }

    @Test
    @DisplayName("404 -> queue URL not found")
    void http404() throws Exception {
        printServerReturns(httpResponse(404, null));

        assertFailure(send(), 404, "IPP queue URL was not found");
    }

    @Test
    @DisplayName("500 -> generic HTTP failure, status passed through")
    void http500() throws Exception {
        printServerReturns(httpResponse(500, null));

        assertFailure(send(), 500, "IPP HTTP request failed with status 500");
    }

    @ParameterizedTest(name = "HTTP {0} -> generic failure mapped to 502")
    @CsvSource({"199", "300", "600"}) // below 2xx, redirect range, above 599
    void nonErrorRangeStatus_mappedTo502(int status) throws Exception {
        printServerReturns(httpResponse(status, null));

        assertFailure(send(), 502, "IPP HTTP request failed with status " + status);
    }

    // 3. processResponse() - IPP body branches

    @Test
    @DisplayName("200 with null body -> not a valid IPP response (502)")
    void nullBody() throws Exception {
        printServerReturns(httpResponse(200, null));

        assertFailure(send(), 502, "HTTP request succeeded, but the response is not a valid IPP response");
    }

    @Test
    @DisplayName("200 with 7-byte body (one short of minimum) -> not a valid IPP response")
    void tooShortBody() throws Exception {
        printServerReturns(httpResponse(200, new byte[7]));

        assertFailure(send(), 502, "HTTP request succeeded, but the response is not a valid IPP response");
    }

    @Test
    @DisplayName("Response request ID differs from item number -> mismatch failure")
    void requestIdMismatch() throws Exception {
        printServerReturns(httpResponse(200, ippBody(0x0000, ITEM_NO + 1)));

        assertFailure(send(), 502, "IPP response request ID mismatch. Expected: 7, received: 8");
    }

    @Test
    @DisplayName("Exactly 8-byte body, status 0x0000, matching ID -> success with full details")
    void success_fullDetails() throws Exception {
        printServerReturns(httpResponse(200, ippBody(0x0000, ITEM_NO)));

        ApiResponse<PrinterIppResponse> result = send();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("The request has been processed");
        PrinterIppResponse printer = result.getResponse();
        assertThat(printer.success()).isTrue();
        assertThat(printer.httpStatus()).isEqualTo(200);
        assertThat(printer.ippStatus()).isEqualTo(0x0000);
        assertThat(printer.requestId()).isEqualTo(ITEM_NO);
        assertThat(printer.data()).isNull();
        assertThat(printer.message())
                .isEqualTo("Print job accepted. IPP version: 1.1, status: 0x0000 successful-ok");
    }

    @Test
    @DisplayName("Boundary: IPP status 0x00FF is still accepted")
    void ippStatus0x00FF_isAccepted() throws Exception {
        printServerReturns(httpResponse(200, ippBody(0x00FF, ITEM_NO)));

        ApiResponse<PrinterIppResponse> result = send();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResponse().message()).contains("unknown-ipp-status-0x00FF");
    }

    @Test
    @DisplayName("Boundary: IPP status 0x0100 is rejected")
    void ippStatus0x0100_isRejected() throws Exception {
        printServerReturns(httpResponse(200, ippBody(0x0100, ITEM_NO)));

        assertFailure(send(), 502,
                "Print job rejected. IPP version: 1.1, status: 0x0100 unknown-ipp-status-0x0100");
    }

    @ParameterizedTest(name = "IPP status {0} -> {1} (accepted={2})")
    @CsvSource({
            "0x0000, successful-ok,                                   true",
            "0x0001, successful-ok-ignored-or-substituted-attributes, true",
            "0x0002, successful-ok-conflicting-attributes,            true",
            "0x0400, client-error-bad-request,                        false",
            "0x0401, client-error-forbidden,                          false",
            "0x0402, client-error-not-authenticated,                  false",
            "0x0403, client-error-not-authorized,                     false",
            "0x0404, client-error-not-possible,                       false",
            "0x0406, client-error-not-found,                          false",
            "0x040A, client-error-document-format-not-supported,      false",
            "0x0500, server-error-internal-error,                     false",
            "0x0501, server-error-operation-not-supported,            false",
            "0x0502, server-error-service-unavailable,                false",
            "0x0503, server-error-version-not-supported,              false",
            "0x0405, unknown-ipp-status-0x0405,                       false"   // default branch
    })
    void ippStatusDescriptions(String hexStatus, String expectedDescription, boolean accepted) throws Exception {
        int ippStatus = Integer.decode(hexStatus);
        printServerReturns(httpResponse(200, ippBody(ippStatus, ITEM_NO)));

        ApiResponse<PrinterIppResponse> result = send();

        assertThat(result.isSuccess()).isEqualTo(accepted);
        String message = accepted ? result.getResponse().message() : result.getMessage();
        assertThat(message)
                .startsWith(accepted ? "Print job accepted." : "Print job rejected.")
                .endsWith(String.format("0x%04X %s", ippStatus, expectedDescription));
    }

    // 4. Exception handling in sendPrintRequest()

    @Test
    @DisplayName("IOException with message -> 502 with that message")
    void ioException_withMessage() throws Exception {
        doThrow(new IOException("Connection refused")).when(httpClient).send(any(), any());

        assertFailure(send(), 502, "IPP communication failed: Connection refused");
    }

    @Test
    @DisplayName("IOException without message -> 502 with exception class name")
    void ioException_withoutMessage() throws Exception {
        doThrow(new IOException()).when(httpClient).send(any(), any());

        assertFailure(send(), 502, "IPP communication failed: IOException");
    }

    @Test
    @DisplayName("HttpTimeoutException (an IOException) -> 502")
    void timeout() throws Exception {
        doThrow(new HttpTimeoutException("request timed out")).when(httpClient).send(any(), any());

        assertFailure(send(), 502, "IPP communication failed: request timed out");
    }

    @Test
    @DisplayName("InterruptedException -> 500 and the thread's interrupt flag is restored")
    void interrupted() throws Exception {
        doThrow(new InterruptedException()).when(httpClient).send(any(), any());

        ApiResponse<PrinterIppResponse> result = send();

        assertFailure(result, 500, "IPP print request was interrupted");
        // Thread.interrupted() checks AND clears the flag, so later tests are not affected.
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    @DisplayName("Unexpected RuntimeException -> 500")
    void unexpectedException() throws Exception {
        doThrow(new IllegalStateException("boom")).when(httpClient).send(any(), any());

        assertFailure(send(), 500, "Unexpected error while sending IPP request: boom");
    }

    // 5. What is actually sent: HTTP request + IPP payload + constructor

    @Test
    @DisplayName("HTTP request: POST to queue URL with IPP headers and Basic auth")
    void httpRequest_isBuiltCorrectly() throws Exception {
        printServerReturns(httpResponse(200, ippBody(0x0000, ITEM_NO)));

        send();

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        HttpRequest request = captor.getValue();

        String expectedAuth = "Basic " + Base64.getEncoder()
                .encodeToString("TEST\\test-user:test-password".getBytes(StandardCharsets.UTF_8));

        assertThat(request.method()).isEqualTo("POST");
        // CURRENT BEHAVIOUR: posts to the queue URL only; printer name is not in the HTTP path.
        assertThat(request.uri()).isEqualTo(URI.create(QUEUE_URL));
        assertThat(request.timeout()).contains(Duration.ofSeconds(1));
        assertThat(request.headers().firstValue("Content-Type")).contains("application/ipp");
        assertThat(request.headers().firstValue("Accept")).contains("application/ipp");
        assertThat(request.headers().firstValue("Authorization")).contains(expectedAuth);
        assertThat(request.bodyPublisher()).isPresent();
        assertThat(request.bodyPublisher().get().contentLength())
                .isEqualTo(buildIppRequest(ITEM_NO, PRINTER_NAME, JOB_NAME).length);
    }

    @Test
    @DisplayName("IPP Print-Job payload: header, 6 attributes in order, end tag, then document")
    void ippPayload_isBuiltCorrectly() throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(buildIppRequest(ITEM_NO, PRINTER_NAME, JOB_NAME));

        assertThat(buffer.get()).as("major version").isEqualTo((byte) 0x01);
        assertThat(buffer.get()).as("minor version").isEqualTo((byte) 0x01);
        assertThat(buffer.getShort()).as("Print-Job operation").isEqualTo((short) 0x0002);
        assertThat(buffer.getInt()).as("request id = item number").isEqualTo(ITEM_NO);
        assertThat(buffer.get()).as("operation-attributes tag").isEqualTo((byte) 0x01);

        assertAttribute(buffer, 0x47, "attributes-charset", "utf-8");
        assertAttribute(buffer, 0x48, "attributes-natural-language", "en");
        assertAttribute(buffer, 0x45, "printer-uri", QUEUE_URL + PRINTER_NAME);
        assertAttribute(buffer, 0x42, "requesting-user-name", "test-user");
        assertAttribute(buffer, 0x42, "job-name", "TEST_JOB_" + JOB_NAME);
        assertAttribute(buffer, 0x49, "document-format", "text/plain");

        assertThat(buffer.get()).as("end-of-attributes tag").isEqualTo((byte) 0x03);

        byte[] document = new byte[buffer.remaining()];
        buffer.get(document);
        assertThat(new String(document, StandardCharsets.UTF_8)).isEqualTo("Unit test print content");
    }

    @Test
    @DisplayName("Constructor builds an HTTP/1.1 client with connect timeout and normal redirects")
    void constructor_configuresRealHttpClient() {
        PrinterConnectivityService fresh = new PrinterConnectivityService(validProperties());
        HttpClient realClient = (HttpClient) ReflectionTestUtils.getField(fresh, "httpClient");

        assertThat(realClient).isNotNull();
        assertThat(realClient.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
        assertThat(realClient.connectTimeout()).contains(Duration.ofSeconds(1));
        assertThat(realClient.followRedirects()).isEqualTo(HttpClient.Redirect.NORMAL);
    }

    /** Calls the private buildIppRequest(int, String, String) via reflection. */
    private byte[] buildIppRequest(int itemNo, String printerName, String jobName) throws Exception {
        Method method = PrinterConnectivityService.class
                .getDeclaredMethod("buildIppRequest", int.class, String.class, String.class);
        method.setAccessible(true);
        return (byte[]) method.invoke(service, itemNo, printerName, jobName);
    }

    /** Reads one IPP attribute (lengths read BEFORE moving the position) and checks it. */
    private static void assertAttribute(ByteBuffer buffer, int expectedTag,
                                        String expectedName, String expectedValue) {
        assertThat(buffer.get()).as("tag of %s", expectedName).isEqualTo((byte) expectedTag);

        byte[] name = new byte[buffer.getShort()];
        buffer.get(name);
        assertThat(new String(name, StandardCharsets.US_ASCII)).isEqualTo(expectedName);

        byte[] value = new byte[buffer.getShort()];
        buffer.get(value);
        assertThat(new String(value, StandardCharsets.UTF_8))
                .as("value of %s", expectedName).isEqualTo(expectedValue);
    }
}