package com.costco.replatform.foundation.printrouter.controller;

import com.costco.replatform.foundation.printrouter.common.enums.DocumentType;
import com.costco.replatform.foundation.printrouter.common.enums.PrintType;
import com.costco.replatform.foundation.printrouter.common.enums.PrinterType;
import com.costco.replatform.foundation.printrouter.model.ActiveDirectoryDiscoveryResponse;
import com.costco.replatform.foundation.printrouter.model.ApiRequest;
import com.costco.replatform.foundation.printrouter.model.ApiResponse;
import com.costco.replatform.foundation.printrouter.model.PrintMetadata;
import com.costco.replatform.foundation.printrouter.model.PrintRouteRequest;
import com.costco.replatform.foundation.printrouter.model.PrintRouteResponse;
import com.costco.replatform.foundation.printrouter.model.PrinterIppResponse;
import com.costco.replatform.foundation.printrouter.model.PrinterMetadata;
import com.costco.replatform.foundation.printrouter.service.ActiveDirectoryDiscoveryService;
import com.costco.replatform.foundation.printrouter.service.PrinterConnectivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PrintRouterControllerTest {

    private ActiveDirectoryDiscoveryService adService;
    private PrinterConnectivityService printService;
    private PrintRouterController controller;

    @BeforeEach
    void setUp() {
        adService = mock(ActiveDirectoryDiscoveryService.class);
        printService = mock(PrinterConnectivityService.class);
        controller = new PrintRouterController(adService, printService);
    }

    // Helpers

    private static PrintMetadata metadata() {
        PrintMetadata m = new PrintMetadata();
        m.setTrackingId("TRK-1");
        m.setRequestTime("2026-11-04T10:00:00");
        m.setRequestedUser("JohnDoe");
        m.setDocumentName("label.pdf");
        m.setDocumentType(DocumentType.PDF);
        m.setPrintType(PrintType.RTLABEL);
        m.setPrinterType(PrinterType.ZEBRA);
        m.setCopies(1);
        m.setItemNo(7);
        m.setLocationId("WH-123");
        return m;
    }

    private static ApiRequest<PrintRouteRequest> apiRequest(PrintRouteRequest request) {
        ApiRequest<PrintRouteRequest> apiRequest = new ApiRequest<>();
        apiRequest.setRequestId("UI-REQ-1");
        apiRequest.setRequest(request);
        return apiRequest;
    }

    private static ApiRequest<PrintRouteRequest> apiRequest(PrintMetadata metadata) {
        PrintRouteRequest request = new PrintRouteRequest();
        request.setPrintMetadata(metadata);
        return apiRequest(request);
    }

    private static PrinterMetadata printer(String name) {
        PrinterMetadata printer = new PrinterMetadata();
        printer.setPrinterName(name);
        return printer;
    }

    /** AD returns this printer list (may contain nulls); pass null for a null list. */
    private void adReturns(List<PrinterMetadata> printers) {
        ActiveDirectoryDiscoveryResponse discovery = new ActiveDirectoryDiscoveryResponse();
        discovery.setPrinterList(printers);
        when(adService.discoverPrintersByLocation(any())).thenReturn(ApiResponse.ok(discovery));
    }

    /** The named printr answers with the given HTTP status. */
    private void printerAnswers(String printerName, int httpStatus) {
        PrinterIppResponse ipp = new PrinterIppResponse(httpStatus, 0, 7, "msg", httpStatus == 200, null);
        when(printService.sendPrintRequest(anyInt(), eq(printerName), any()))
                .thenReturn(ApiResponse.ok(ipp));
    }

    private static void assertRouted(ResponseEntity<ApiResponse<PrintRouteResponse>> result) {
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getMessage()).isEqualTo("The request has been processed");
        assertThat(result.getBody().getResponse().getPrintStatus()).isEqualTo("PRINT_REQUESTED");
    }

    /** CURRENT BEHAVIOUR: the 400 body is the untouched init() response (success=true). */
    private static void assertNotRouted(ResponseEntity<ApiResponse<PrintRouteResponse>> result) {
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getMessage()).isEqualTo("The request has been initiated.");
        assertThat(result.getBody().getResponse().getPrintStatus()).isNull();
    }

    // 1. Input shape

    @Test
    @DisplayName("Null ApiRequest -> 400; no discovery, no printing")
    void nullApiRequest_badRequest() {
        assertNotRouted(controller.routePrint(null));

        verifyNoInteractions(adService, printService);
    }

    @Test
    @DisplayName("Null inner request -> discovery with null location; no printers -> 400")
    void nullInnerRequest_discoversWithNullLocation() {
        adReturns(List.of());

        assertNotRouted(controller.routePrint(apiRequest((PrintRouteRequest) null)));

        verify(adService).discoverPrintersByLocation(isNull());
    }

    @Test
    @DisplayName("CURRENT BEHAVIOUR: null metadata still prints - itemNo 0, null document name")
    void nullMetadata_stillPrintsWithDefaults() {
        adReturns(List.of(printer("Laser1")));
        printerAnswers("Laser1", 200);

        assertRouted(controller.routePrint(apiRequest((PrintMetadata) null)));

        verify(adService).discoverPrintersByLocation(isNull());
        verify(printService).sendPrintRequest(eq(0), eq("Laser1"), isNull());
    }

    // 2. Routing across printers

    @Test
    @DisplayName("First printer returns 200 -> routed; later printers are not tried")
    void firstPrinterSucceeds_routed() {
        adReturns(List.of(printer("Laser1"), printer("Laser2")));
        printerAnswers("Laser1", 200);

        assertRouted(controller.routePrint(apiRequest(metadata())));

        verify(adService).discoverPrintersByLocation("WH-123");
        verify(printService).sendPrintRequest(7, "Laser1", "label.pdf");
        verify(printService, never()).sendPrintRequest(anyInt(), eq("Laser2"), any());
    }

    @Test
    @DisplayName("First printer fails (502), second returns 200 -> routed via fallback, in order")
    void fallbackToSecondPrinter_routed() {
        adReturns(List.of(printer("Laser1"), printer("Laser2")));
        printerAnswers("Laser1", 502);
        printerAnswers("Laser2", 200);

        assertRouted(controller.routePrint(apiRequest(metadata())));

        InOrder order = inOrder(printService);
        order.verify(printService).sendPrintRequest(7, "Laser1", "label.pdf");
        order.verify(printService).sendPrintRequest(7, "Laser2", "label.pdf");
    }

    @Test
    @DisplayName("Null entries in the printer list are skipped")
    void nullPrinterEntry_skipped() {
        adReturns(Arrays.asList(null, printer("Laser1")));
        printerAnswers("Laser1", 200);

        assertRouted(controller.routePrint(apiRequest(metadata())));
    }

    @Test
    @DisplayName("Null print response, then response with null payload -> both skipped -> 400")
    void nullPrintResponses_skipped() {
        adReturns(List.of(printer("Laser1"), printer("Laser2")));
        when(printService.sendPrintRequest(anyInt(), eq("Laser1"), any())).thenReturn(null);
        when(printService.sendPrintRequest(anyInt(), eq("Laser2"), any()))
                .thenReturn(ApiResponse.failures("ERR_001", "IPP communication failed", 502));

        assertNotRouted(controller.routePrint(apiRequest(metadata())));

        verify(printService).sendPrintRequest(7, "Laser1", "label.pdf");
        verify(printService).sendPrintRequest(7, "Laser2", "label.pdf");
    }

    @Test
    @DisplayName("Boundary: printer returns 201 (not exactly 200) -> not treated as routed -> 400")
    void http201_notTreatedAsSuccess() {
        adReturns(List.of(printer("Laser1")));
        printerAnswers("Laser1", 201);

        assertNotRouted(controller.routePrint(apiRequest(metadata())));
    }

    @Test
    @DisplayName("Empty printer list -> 400, nothing printed")
    void emptyPrinterList_badRequest() {
        adReturns(List.of());

        assertNotRouted(controller.routePrint(apiRequest(metadata())));

        verifyNoInteractions(printService);
    }

    @Test
    @DisplayName("Null printer list (e.g. AD failure) -> 400, nothing printed")
    void nullPrinterList_badRequest() {
        adReturns(null);

        assertNotRouted(controller.routePrint(apiRequest(metadata())));

        verifyNoInteractions(printService);
    }

    // 3. Crash paths (CURRENT BEHAVIOUR - no validation on the request body)

    @Test
    @DisplayName("CURRENT BEHAVIOUR: missing documentType -> NullPointerException (would be HTTP 500)")
    void missingDocumentType_throwsNpe() {
        PrintMetadata metadata = metadata();
        metadata.setDocumentType(null);

        assertThatThrownBy(() -> controller.routePrint(apiRequest(metadata)))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(adService, printService);
    }

    @Test
    @DisplayName("CURRENT BEHAVIOUR: missing itemNo -> NullPointerException when a printer is found")
    void missingItemNo_throwsNpeWhenPrinting() {
        PrintMetadata metadata = metadata();
        metadata.setItemNo(null);
        adReturns(List.of(printer("Laser1")));

        assertThatThrownBy(() -> controller.routePrint(apiRequest(metadata)))
                .isInstanceOf(NullPointerException.class);
    }

    // 4. mask() - private static helper used when logging the user id

    static Stream<Arguments> maskCases() {
        return Stream.of(
                arguments(null, null),
                arguments("", ""),
                arguments("   ", "   "),
                arguments("A", "*"),
                arguments("AB", "**"),
                arguments("ABC", "A*C"),
                arguments("JohnDoe", "J*****e"));
    }

    @ParameterizedTest(name = "mask({0}) = {1}")
    @MethodSource("maskCases")
    void mask_hidesMiddleCharacters(String input, String expected) throws Exception {
        Method mask = PrintRouterController.class.getDeclaredMethod("mask", String.class);
        mask.setAccessible(true);

        assertThat(mask.invoke(null, input)).isEqualTo(expected); // static method -> null target
    }
}