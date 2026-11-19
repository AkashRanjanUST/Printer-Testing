package com.costco.replatform.foundation.printrouter.service;

import com.costco.replatform.foundation.printrouter.config.PrinterIppProperties;
import com.costco.replatform.foundation.printrouter.model.ApiResponse;
import com.costco.replatform.foundation.printrouter.model.PrinterIppResponse;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: the REAL java.net.http.HttpClient inside PrinterConnectivityService
 * talks over a REAL socket to WireMock, which plays the Windows IPP print server.
 */
class PrinterConnectivityServiceIT {

    @RegisterExtension
    static WireMockExtension printServer = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final String QUEUE_PATH = "/printers/";
    private static final int ITEM_NO = 7;
    private static final String PRINT_CONTENT = "Integration test print content";

    private PrinterConnectivityService service;

    @BeforeEach
    void setUp() {
        PrinterIppProperties properties = new PrinterIppProperties();
        properties.setQueueUrl(printServer.baseUrl() + QUEUE_PATH);
        properties.setRequestingUser("test-user");
        properties.setDomainUsername("TEST\\test-user");
        properties.setPassword("test-password");
        properties.setJobName("IT_JOB_");
        properties.setPrintContent(PRINT_CONTENT);
        properties.setConnectTimeoutSeconds(1);
        properties.setRequestTimeoutSeconds(1);

        service = new PrinterConnectivityService(properties); // real HttpClient, no mocks
    }

    /** An 8-byte IPP response header: version 1.1, status, request id. */
    private static byte[] ippResponse(int ippStatus, int requestId) {
        return ByteBuffer.allocate(8)
                .put((byte) 1).put((byte) 1)
                .putShort((short) ippStatus)
                .putInt(requestId)
                .array();
    }

    private static String expectedBasicAuth() {
        return "Basic " + Base64.getEncoder()
                .encodeToString("TEST\\test-user:test-password".getBytes(StandardCharsets.UTF_8));
    }

    private ApiResponse<PrinterIppResponse> send() {
        return service.sendPrintRequest(ITEM_NO, "Laser1", "JOB");
    }

    // Happy path - what actually goes over the wire

    @Test
    @DisplayName("Accepted job: correct headers, Basic auth and IPP Print-Job body arrive at the server")
    void acceptedJob_wireLevelRequestIsCorrect() {
        printServer.stubFor(post(urlEqualTo(QUEUE_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/ipp")
                        .withBody(ippResponse(0x0000, ITEM_NO))));

        ApiResponse<PrinterIppResponse> result = send();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResponse().httpStatus()).isEqualTo(200);
        assertThat(result.getResponse().ippStatus()).isZero();
        assertThat(result.getResponse().message()).startsWith("Print job accepted.");

        printServer.verify(1, postRequestedFor(urlEqualTo(QUEUE_PATH))
                .withHeader("Content-Type", equalTo("application/ipp"))
                .withHeader("Accept", equalTo("application/ipp"))
                .withHeader("Authorization", equalTo(expectedBasicAuth())));

        List<LoggedRequest> requests = printServer.findAll(postRequestedFor(urlEqualTo(QUEUE_PATH)));
        byte[] body = requests.get(0).getBody();
        ByteBuffer header = ByteBuffer.wrap(body, 0, 8);
        assertThat(header.get()).as("IPP major version").isEqualTo((byte) 1);
        assertThat(header.get()).as("IPP minor version").isEqualTo((byte) 1);
        assertThat(header.getShort()).as("Print-Job operation").isEqualTo((short) 0x0002);
        assertThat(header.getInt()).as("request id = item number").isEqualTo(ITEM_NO);

        byte[] document = PRINT_CONTENT.getBytes(StandardCharsets.UTF_8);
        byte[] bodyEnd = Arrays.copyOfRange(body, body.length - document.length, body.length);
        assertThat(bodyEnd).as("document follows the end-of-attributes tag").isEqualTo(document);
        assertThat(body[body.length - document.length - 1]).as("end-of-attributes tag").isEqualTo((byte) 0x03);
    }

    @Test
    @DisplayName("307 redirect is followed (client built with Redirect.NORMAL) and still succeeds")
    void redirect_isFollowed() {
        printServer.stubFor(post(urlEqualTo(QUEUE_PATH))
                .willReturn(aResponse().withStatus(307).withHeader("Location", "/printers-moved/")));
        printServer.stubFor(post(urlEqualTo("/printers-moved/"))
                .willReturn(aResponse().withStatus(200).withBody(ippResponse(0x0000, ITEM_NO))));

        assertThat(send().isSuccess()).isTrue();
        printServer.verify(1, postRequestedFor(urlEqualTo("/printers-moved/")));
    }

    // Print server answers, but not with success

    @Test
    @DisplayName("IPP rejection (0x0406 client-error-not-found) over HTTP 200 -> failure, 502")
    void ippRejection_isReported() {
        printServer.stubFor(post(urlEqualTo(QUEUE_PATH))
                .willReturn(aResponse().withStatus(200).withBody(ippResponse(0x0406, ITEM_NO))));

        ApiResponse<PrinterIppResponse> result = send();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorStatus()).isEqualTo("502");
        assertThat(result.getMessage()).contains("Print job rejected.").contains("client-error-not-found");
    }

    @Test
    @DisplayName("401 with NTLM challenge -> failure reporting the server's auth scheme")
    void unauthorized_reportsScheme() {
        printServer.stubFor(post(urlEqualTo(QUEUE_PATH))
                .willReturn(aResponse().withStatus(401).withHeader("WWW-Authenticate", "NTLM")));

        ApiResponse<PrinterIppResponse> result = send();

        assertThat(result.getErrorStatus()).isEqualTo("401");
        assertThat(result.getMessage()).isEqualTo("HTTP authentication failed. Scheme: NTLM");
    }

    @Test
    @DisplayName("404 from the print server -> queue not found")
    void notFound_isReported() {
        printServer.stubFor(post(urlEqualTo(QUEUE_PATH)).willReturn(aResponse().withStatus(404)));

        ApiResponse<PrinterIppResponse> result = send();

        assertThat(result.getErrorStatus()).isEqualTo("404");
        assertThat(result.getMessage()).isEqualTo("IPP queue URL was not found");
    }

    @Test
    @DisplayName("200 with a truncated (3-byte) body -> not a valid IPP response")
    void truncatedBody_isRejected() {
        printServer.stubFor(post(urlEqualTo(QUEUE_PATH))
                .willReturn(aResponse().withStatus(200).withBody(new byte[]{1, 1, 0})));

        ApiResponse<PrinterIppResponse> result = send();

        assertThat(result.getErrorStatus()).isEqualTo("502");
        assertThat(result.getMessage()).contains("is not a valid IPP response");
    }

    @Test
    @DisplayName("Response for a different request ID -> mismatch failure")
    void requestIdMismatch_isRejected() {
        printServer.stubFor(post(urlEqualTo(QUEUE_PATH))
                .willReturn(aResponse().withStatus(200).withBody(ippResponse(0x0000, 999))));

        ApiResponse<PrinterIppResponse> result = send();

        assertThat(result.getMessage()).isEqualTo("IPP response request ID mismatch. Expected: 7, received: 999");
    }

    // Network failures that are hard to reproduce against a real printer

    @Test
    @DisplayName("Server slower than the 1s request timeout -> real HttpTimeoutException -> 502")
    void slowServer_timesOut() {
        printServer.stubFor(post(urlEqualTo(QUEUE_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withBody(ippResponse(0x0000, ITEM_NO))
                        .withFixedDelay(3000)));

        long start = System.nanoTime();
        ApiResponse<PrinterIppResponse> result = send();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(result.getErrorStatus()).isEqualTo("502");
        assertThat(result.getMessage()).isEqualTo("IPP communication failed: request timed out");
        assertThat(elapsedMillis).as("gave up near the 1s timeout, not after 3s").isLessThan(2500);
    }

    @Test
    @DisplayName("Connection reset by the server -> real IOException -> 502")
    void connectionReset_isReported() {
        printServer.stubFor(post(urlEqualTo(QUEUE_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        ApiResponse<PrinterIppResponse> result = send();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorStatus()).isEqualTo("502");
        assertThat(result.getMessage()).startsWith("IPP communication failed:");
    }
}