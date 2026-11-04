package com.costco.replatform.foundation.printrouter.exception;

import com.costco.replatform.foundation.printrouter.model.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import jakarta.servlet.http.HttpServletRequest;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        // MessageSource is @Autowired but never used, so it can stay null.
        handler = new GlobalExceptionHandler();
    }

    /** A dummy method, only used to build a real MethodParameter for the exception. */
    @SuppressWarnings("unused")
    private void dummyEndpoint(Object body) {
    }

    /** Builds a real MethodArgumentNotValidException with the given field errors. */
    private MethodArgumentNotValidException validationException(FieldError... fieldErrors)
            throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "printRouteRequest");
        for (FieldError fieldError : fieldErrors) {
            bindingResult.addError(fieldError);
        }
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyEndpoint", Object.class);
        return new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);
    }

    private static void assertBadRequest(ResponseEntity<ApiResponse<Void>> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getErrorCode()).isEqualTo("ERR_001");
        assertThat(response.getBody().getErrorStatus()).isEqualTo("400");
        assertThat(response.getBody().getResponse()).isNull();
    }

    @Nested
    @DisplayName("handleValidationExceptions")
    class HandleValidationExceptions {

        @Test
        @DisplayName("One field error -> 400 with {field=message}")
        void singleFieldError() throws Exception {
            MethodArgumentNotValidException ex = validationException(
                    new FieldError("printRouteRequest", "locationId", "must not be blank"));

            ResponseEntity<ApiResponse<Void>> response = handler.handleValidationExceptions(ex);

            assertBadRequest(response);
            assertThat(response.getBody().getMessage()).isEqualTo("{locationId=must not be blank}");
        }

        @Test
        @DisplayName("Two field errors -> 400 with both fields in the message")
        void multipleFieldErrors() throws Exception {
            MethodArgumentNotValidException ex = validationException(
                    new FieldError("printRouteRequest", "locationId", "must not be blank"),
                    new FieldError("printRouteRequest", "itemNo", "must be positive"));

            ResponseEntity<ApiResponse<Void>> response = handler.handleValidationExceptions(ex);

            assertBadRequest(response);
            // HashMap order is not guaranteed, so check each entry separately
            assertThat(response.getBody().getMessage())
                    .contains("locationId=must not be blank")
                    .contains("itemNo=must be positive");
        }

        @Test
        @DisplayName("No field errors -> 400 with empty map message")
        void noFieldErrors() throws Exception {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleValidationExceptions(validationException());

            assertBadRequest(response);
            assertThat(response.getBody().getMessage()).isEqualTo("{}");
        }

        @Test
        @DisplayName("Exception with null binding result -> 400 with empty map message")
        void nullBindingResult() throws Exception {
            MethodArgumentNotValidException ex = spy(validationException());
            doReturn(null).when(ex).getBindingResult();
            doReturn("validation failed").when(ex).getMessage(); // keeps logging safe

            ResponseEntity<ApiResponse<Void>> response = handler.handleValidationExceptions(ex);

            assertBadRequest(response);
            assertThat(response.getBody().getMessage()).isEqualTo("{}");
        }

        @Test
        @DisplayName("Null exception -> 400 with empty map message")
        void nullException() {
            ResponseEntity<ApiResponse<Void>> response = handler.handleValidationExceptions(null);

            assertBadRequest(response);
            assertThat(response.getBody().getMessage()).isEqualTo("{}");
        }
    }

    @Nested
    @DisplayName("handleIllegalArgExceptions")
    class HandleIllegalArgExceptions {

        @Test
        @DisplayName("Exception with message -> 400 with that message")
        void withMessage() {
            ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgExceptions(
                    new IllegalArgumentException("queueUrl must not be blank"));

            assertBadRequest(response);
            assertThat(response.getBody().getMessage()).isEqualTo("queueUrl must not be blank");
        }

        @Test
        @DisplayName("Exception without message -> 400 with null message")
        void withoutMessage() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleIllegalArgExceptions(new IllegalArgumentException());

            assertBadRequest(response);
            assertThat(response.getBody().getMessage()).isNull();
        }

        @Test
        @DisplayName("Null exception -> 400 with fallback message")
        void nullException() {
            ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgExceptions(null);

            assertBadRequest(response);
            assertThat(response.getBody().getMessage()).isEqualTo("Illegal Argument Exception.");
        }
    }

    @Nested
    @DisplayName("getRequestInfo (private, currently unused)")
    class GetRequestInfo {

        private String invoke(HttpServletRequest request) throws Exception {
            Method method = GlobalExceptionHandler.class
                    .getDeclaredMethod("getRequestInfo", HttpServletRequest.class);
            method.setAccessible(true);
            return (String) method.invoke(handler, request);
        }

        @Test
        @DisplayName("Null request -> 'Unknown Request'")
        void nullRequest() throws Exception {
            assertThat(invoke(null)).isEqualTo("Unknown Request");
        }

        @Test
        @DisplayName("Real request -> 'METHOD URI'")
        void realRequest() throws Exception {
            MockHttpServletRequest request =
                    new MockHttpServletRequest("POST", "/api/v1/printroute/send");

            assertThat(invoke(request)).isEqualTo("POST /api/v1/printroute/send");
        }
    }
}