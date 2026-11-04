package com.costco.replatform.foundation.printrouter.controller;

import com.costco.replatform.foundation.printrouter.exception.GlobalExceptionHandler;
import com.costco.replatform.foundation.printrouter.model.ActiveDirectoryDiscoveryResponse;
import com.costco.replatform.foundation.printrouter.model.ApiResponse;
import com.costco.replatform.foundation.printrouter.model.PrinterIppResponse;
import com.costco.replatform.foundation.printrouter.model.PrinterMetadata;
import com.costco.replatform.foundation.printrouter.service.ActiveDirectoryDiscoveryService;
import com.costco.replatform.foundation.printrouter.service.PrinterConnectivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PrintRouterControllerWebTest {

    private static final String URL = "/api/v1/printroute/send";

    /** A complete, realistic request body as a warehouse app would send it. */
    private static final String VALID_JSON = """
            {
              "requestId": "UI-REQ-1",
              "source": "warehouse-ui",
              "request": {
                "printMetadata": {
                  "trackingId": "TRK-1",
                  "requestTime": "2026-11-04T10:00:00",
                  "requestedUser": "JohnDoe",
                  "documentName": "label.pdf",
                  "documentType": "PDF",
                  "printType": "RTLABEL",
                  "printerType": "ZEBRA",
                  "copies": 1,
                  "itemNo": 7,
                  "locationId": "WH-123"
                }
              }
            }
            """;

    private ActiveDirectoryDiscoveryService adService;
    private PrinterConnectivityService printService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adService = mock(ActiveDirectoryDiscoveryService.class);
        printService = mock(PrinterConnectivityService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PrintRouterController(adService, printService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addPlaceholderValue("app.url.api.name", "api") // resolves /${app.url.api.name}/...
                .build();
    }

    private void adReturns(List<PrinterMetadata> printers) {
        ActiveDirectoryDiscoveryResponse discovery = new ActiveDirectoryDiscoveryResponse();
        discovery.setPrinterList(printers);
        when(adService.discoverPrintersByLocation(any())).thenReturn(ApiResponse.ok(discovery));
    }

    private static PrinterMetadata printer(String name) {
        PrinterMetadata printer = new PrinterMetadata();
        printer.setPrinterName(name);
        return printer;
    }

    @Test
    @DisplayName("Valid JSON + printer accepts -> 200 JSON with PRINT_REQUESTED; JSON fields reach the services")
    void validRequest_routed_returns200Json() throws Exception {
        adReturns(List.of(printer("Laser1")));
        when(printService.sendPrintRequest(anyInt(), eq("Laser1"), any())).thenReturn(
                ApiResponse.ok(new PrinterIppResponse(200, 0, 7, "Print job accepted", true, null)));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("The request has been processed"))
                .andExpect(jsonPath("$.response.printStatus").value("PRINT_REQUESTED"))
                .andExpect(jsonPath("$.errorCode").doesNotExist());   // @JsonInclude(NON_EMPTY)

        // Proves JSON -> ApiRequest<PrintRouteRequest> mapping, including numbers and enums
        verify(adService).discoverPrintersByLocation("WH-123");
        verify(printService).sendPrintRequest(7, "Laser1", "label.pdf");
    }

    @Test
    @DisplayName("No printers for the location -> 400 JSON, no printStatus")
    void noPrinters_returns400Json() throws Exception {
        adReturns(List.of());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request has been initiated."))
                .andExpect(jsonPath("$.response.printStatus").doesNotExist());
    }

    @Test
    @DisplayName("Service throws IllegalArgumentException -> GlobalExceptionHandler -> 400 with ERR_001")
    void illegalArgument_handledByControllerAdvice() throws Exception {
        when(adService.discoverPrintersByLocation(any()))
                .thenThrow(new IllegalArgumentException("Invalid location"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ERR_001"))
                .andExpect(jsonPath("$.errorStatus").value("400"))
                .andExpect(jsonPath("$.message").value("Invalid location"));
    }

    @Test
    @DisplayName("Malformed JSON -> 400 from Spring, services never called")
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{ not valid json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adService, printService);
    }

    @Test
    @DisplayName("Unknown enum value (documentType=DOCX) -> 400, services never called")
    void unknownEnumValue_returns400() throws Exception {
        String json = VALID_JSON.replace("\"PDF\"", "\"DOCX\"");

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adService, printService);
    }

    @Test
    @DisplayName("GET instead of POST -> 405 Method Not Allowed")
    void wrongMethod_returns405() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Non-JSON content type -> 415 Unsupported Media Type")
    void wrongContentType_returns415() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(adService, printService);
    }
}