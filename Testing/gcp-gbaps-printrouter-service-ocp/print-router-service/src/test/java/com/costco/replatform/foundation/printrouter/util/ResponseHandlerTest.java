package com.costco.replatform.foundation.printrouter.util;

import com.costco.replatform.foundation.printrouter.model.PrinterIppResponse;
import com.costco.replatform.foundation.printrouter.model.PrinterIppResponse.ResponseData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseHandlerTest {

    private static final String TRACKING_ID = "TRK-001";
    private static final String GENERIC_500_MESSAGE =
            "Internal server error while processing vendor creation";

    private ResponseHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ResponseHandler(); // no dependencies, so no Spring or mocks needed
    }

    private static PrinterIppResponse serviceResponseWith(ResponseData data) {
        return new PrinterIppResponse(400, null, 1, "service message", false, data);
    }

    private static ResponseData data(Integer status, String message) {
        ResponseData data = new ResponseData();
        data.setStatus(status);
        data.setMessage(message);
        return data;
    }

    @Nested
    @DisplayName("buildServiceErrorResponse")
    class BuildServiceErrorResponse {

        @Test
        @DisplayName("Null service response -> 500 with generic message")
        void nullResponse_returns500() {
            PrinterIppResponse result = handler.buildServiceErrorResponse(TRACKING_ID, null);

            assertThat(result.data().isOk()).isFalse();
            assertThat(result.data().getStatus()).isEqualTo(500);
            assertThat(result.data().getMessage()).isEqualTo(GENERIC_500_MESSAGE);
        }

        @Test
        @DisplayName("Service response with null data -> 500 with generic message")
        void nullData_returns500() {
            PrinterIppResponse result =
                    handler.buildServiceErrorResponse(TRACKING_ID, serviceResponseWith(null));

            assertThat(result.data().isOk()).isFalse();
            assertThat(result.data().getStatus()).isEqualTo(500);
            assertThat(result.data().getMessage()).isEqualTo(GENERIC_500_MESSAGE);
        }

        @Test
        @DisplayName("Data with status and message -> both passed through")
        void dataWithStatusAndMessage_passedThrough() {
            PrinterIppResponse result = handler.buildServiceErrorResponse(
                    TRACKING_ID, serviceResponseWith(data(404, "Printer not found")));

            assertThat(result.data().isOk()).isFalse();
            assertThat(result.data().getStatus()).isEqualTo(404);
            assertThat(result.data().getMessage()).isEqualTo("Printer not found");
        }

        @Test
        @DisplayName("Data with status 0 (never set) -> falls back to 500")
        void dataWithZeroStatus_fallsBackTo500() {
            PrinterIppResponse result = handler.buildServiceErrorResponse(
                    TRACKING_ID, serviceResponseWith(data(null, "Some failure")));

            assertThat(result.data().getStatus()).isEqualTo(500);
            assertThat(result.data().getMessage()).isEqualTo("Some failure");
        }

        @Test
        @DisplayName("Data with null message -> default service error message")
        void dataWithNullMessage_usesDefaultMessage() {
            PrinterIppResponse result = handler.buildServiceErrorResponse(
                    TRACKING_ID, serviceResponseWith(data(502, null)));

            assertThat(result.data().getStatus()).isEqualTo(502);
            assertThat(result.data().getMessage())
                    .isEqualTo("Service error occurred during vendor creation");
        }
    }

    @Nested
    @DisplayName("resolveHttpStatus")
    class ResolveHttpStatus {

        @ParameterizedTest(name = "service status {0} -> HTTP {1}")
        @CsvSource({
                "1,    1",      // boundary: smallest positive value passes through
                "200,  200",
                "404,  404",
                "599,  599",
                "0,    500",    // boundary: zero falls back
                "-1,   500",    // negative falls back
                "-500, 500"
        })
        void resolvesStatus(int serviceStatus, int expectedHttpStatus) {
            assertThat(handler.resolveHttpStatus(serviceStatus)).isEqualTo(expectedHttpStatus);
        }
    }

    @Nested
    @DisplayName("buildErrorResponse")
    class BuildErrorResponse {

        @Test
        @DisplayName("Inner data is populated correctly (ok=false, status, message)")
        void dataFields_areCorrect() {
            PrinterIppResponse result = handler.buildErrorResponse(TRACKING_ID, 503, "Unavailable");

            assertThat(result.data().isOk()).isFalse();
            assertThat(result.data().getStatus()).isEqualTo(503);
            assertThat(result.data().getMessage()).isEqualTo("Unavailable");
        }

        @Test
        @DisplayName("CURRENT BEHAVIOUR - looks inverted: error response reports success=true")
        void topLevelFields_currentBehaviour() {
            PrinterIppResponse result = handler.buildErrorResponse(TRACKING_ID, 503, "Unavailable");

            // If ResponseHandler is fixed, update these expectations.
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("IPP request is Success");
            assertThat(result.httpStatus()).isZero();
            assertThat(result.requestId()).isZero();
        }

        @Test
        @DisplayName("CURRENT BEHAVIOUR - random ID lands in ippStatus, within 1..Integer.MAX_VALUE-1")
        void randomId_isInIppStatusField() {
            PrinterIppResponse result = handler.buildErrorResponse(TRACKING_ID, 500, "x");

            assertThat(result.ippStatus()).isBetween(1, Integer.MAX_VALUE - 1);
        }
    }

    @Nested
    @DisplayName("buildSuccessResponse (private, currently unused)")
    class BuildSuccessResponse {

        @Test
        @DisplayName("CURRENT BEHAVIOUR - looks inverted: success response reports success=false")
        void populatesFields_currentBehaviour() throws Exception {
            Method method = ResponseHandler.class.getDeclaredMethod(
                    "buildSuccessResponse", String.class, int.class, String.class);
            method.setAccessible(true);

            PrinterIppResponse result =
                    (PrinterIppResponse) method.invoke(handler, TRACKING_ID, 200, "Printed");

            assertThat(result.data().isOk()).isTrue();
            assertThat(result.data().getStatus()).isEqualTo(200);
            assertThat(result.data().getMessage()).isEqualTo("Printed");
            // If ResponseHandler is fixed, update these expectations.
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("IPP request interrupted");
            assertThat(result.ippStatus()).isBetween(1, Integer.MAX_VALUE - 1);
        }
    }
}