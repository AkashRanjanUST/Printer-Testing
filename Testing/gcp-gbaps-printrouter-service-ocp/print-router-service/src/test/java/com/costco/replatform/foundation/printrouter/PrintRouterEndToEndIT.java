package com.costco.replatform.foundation.printrouter;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end: real HTTP request -> full Spring Boot app
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PrintRouterEndToEndIT {

    private static final String BASE_DN = "DC=test,DC=invalid";
    private static final String PRINTERS_OU = "OU=Printers," + BASE_DN;
    private static final String BIND_DN = "CN=svc-print," + BASE_DN;
    private static final String BIND_PASSWORD = "test-bind-password";
    private static final String QUEUE_PATH = "/printers/";
    private static final int ITEM_NO = 7;

    private static final InMemoryDirectoryServer DIRECTORY = startDirectory();
    private static final WireMockServer PRINT_SERVER = startPrintServer();

    private static InMemoryDirectoryServer startDirectory() {
        try {
            InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE_DN);
            config.setSchema(null);
            config.addAdditionalBindCredentials(BIND_DN, BIND_PASSWORD);
            config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("ldap", 0));
            InMemoryDirectoryServer server = new InMemoryDirectoryServer(config);
            server.startListening();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException("Could not start in-memory LDAP server", e);
        }
    }

    private static WireMockServer startPrintServer() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        return server;
    }

    @DynamicPropertySource
    static void wireApplicationToTestServers(DynamicPropertyRegistry registry) {
        registry.add("printer.ipp.queue-url", () -> PRINT_SERVER.baseUrl() + QUEUE_PATH);
        registry.add("jndi.ldap.protocol", () -> "ldap://");
        registry.add("jndi.ldap.host", () -> "localhost");
        registry.add("jndi.ldap.port", () -> ":" + DIRECTORY.getListenPort());
        registry.add("jndi.ldap.bind.user", () -> BIND_DN);
        registry.add("jndi.ldap.bind.password", () -> BIND_PASSWORD);
        registry.add("jndi.ldap.base.dn", () -> BASE_DN);
    }

    @AfterAll
    static void stopTestServers() {
        PRINT_SERVER.stop();
        DIRECTORY.shutDown(true);
    }

    @Value("${local.server.port}")
    private int appPort;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void resetTestServers() throws Exception {
        PRINT_SERVER.resetAll();
        DIRECTORY.clear();
        DIRECTORY.add("dn: " + BASE_DN, "objectClass: top", "objectClass: domain", "dc: test");
        DIRECTORY.add("dn: " + PRINTERS_OU, "objectClass: top", "objectClass: organizationalUnit", "ou: Printers");
    }

    // Helpers

    private static void addPrintQueue(String cn, String location) throws Exception {
        DIRECTORY.add(
                "dn: CN=" + cn + "," + PRINTERS_OU,
                "objectClass: printQueue",
                "objectCategory: printQueue",
                "cn: " + cn,
                "printShareName: " + cn.toUpperCase(),
                "shortServerName: PRINTSRV01",
                "location: " + location,
                "driverName: Test Driver");
    }

    private static byte[] ippResponse(int ippStatus) {
        return ByteBuffer.allocate(8)
                .put((byte) 1).put((byte) 1)
                .putShort((short) ippStatus)
                .putInt(ITEM_NO)
                .array();
    }

    /** The print server answers jobs for this printer (matched by printer-uri in the IPP body). */
    private static void printerAnswers(String printerName, int httpStatus, int ippStatus) {
        PRINT_SERVER.stubFor(post(urlEqualTo(QUEUE_PATH))
                .withRequestBody(containing(QUEUE_PATH + printerName))
                .willReturn(aResponse().withStatus(httpStatus)
                        .withHeader("Content-Type", "application/ipp")
                        .withBody(ippResponse(ippStatus))));
    }

    private HttpResponse<String> sendPrintRequest(String locationId) throws Exception {
        String json = """
                {
                  "requestId": "E2E-REQ-1",
                  "source": "warehouse-ui",
                  "request": {
                    "printMetadata": {
                      "trackingId": "TRK-E2E-1",
                      "requestTime": "2026-11-04T10:00:00",
                      "requestedUser": "JohnDoe",
                      "documentName": "label.pdf",
                      "documentType": "PDF",
                      "printType": "RTLABEL",
                      "printerType": "ZEBRA",
                      "copies": 1,
                      "itemNo": %d,
                      "locationId": "%s"
                    }
                  }
                }
                """.formatted(ITEM_NO, locationId);

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + appPort + "/api/v1/printroute/send"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // End-to-end scenarios

    @Test
    @DisplayName("Happy path: AD finds the warehouse printer, print server accepts -> 200 PRINT_REQUESTED")
    void happyPath_printRouted() throws Exception {
        addPrintQueue("Laser1", "01680/LA/WHS/MGROFC");
        printerAnswers("Laser1", 200, 0x0000);

        HttpResponse<String> response = sendPrintRequest("01680");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"printStatus\":\"PRINT_REQUESTED\"");
        PRINT_SERVER.verify(1, postRequestedFor(urlEqualTo(QUEUE_PATH))
                .withRequestBody(containing(QUEUE_PATH + "Laser1")));
    }

    @Test
    @DisplayName("Fallback: one printer rejects (503), the other accepts -> 200, job lands on the working printer")
    void fallback_toWorkingPrinter() throws Exception {
        addPrintQueue("Laser1", "01680/LA/WHS/MGROFC");
        addPrintQueue("Laser2", "01680/LA/WHS/RECEIVING");
        printerAnswers("Laser1", 503, 0x0000);
        printerAnswers("Laser2", 200, 0x0000);

        HttpResponse<String> response = sendPrintRequest("01680");

        assertThat(response.statusCode()).isEqualTo(200);
        PRINT_SERVER.verify(1, postRequestedFor(urlEqualTo(QUEUE_PATH))
                .withRequestBody(containing(QUEUE_PATH + "Laser2")));
    }

    @Test
    @DisplayName("No printers registered for the warehouse -> 400, print server never contacted")
    void noPrintersForLocation_badRequest() throws Exception {
        addPrintQueue("Laser9", "00999/WA/WHS/MGROFC");

        HttpResponse<String> response = sendPrintRequest("01680");

        assertThat(response.statusCode()).isEqualTo(400);
        PRINT_SERVER.verify(0, postRequestedFor(urlEqualTo(QUEUE_PATH)));
    }

    @Test
    @DisplayName("Every printer rejects the job (IPP 0x0406) -> 400")
    void allPrintersReject_badRequest() throws Exception {
        addPrintQueue("Laser1", "01680/LA/WHS/MGROFC");
        addPrintQueue("Laser2", "01680/LA/WHS/RECEIVING");
        printerAnswers("Laser1", 200, 0x0406);
        printerAnswers("Laser2", 200, 0x0406);

        HttpResponse<String> response = sendPrintRequest("01680");

        assertThat(response.statusCode()).isEqualTo(400);
        PRINT_SERVER.verify(2, postRequestedFor(urlEqualTo(QUEUE_PATH)));
    }

    @Test
    @DisplayName("Malformed JSON from the client -> 400 from the real server, nothing downstream is called")
    void malformedJson_badRequest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + appPort + "/api/v1/printroute/send"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{ not json"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        PRINT_SERVER.verify(0, postRequestedFor(urlEqualTo(QUEUE_PATH)));
    }
}