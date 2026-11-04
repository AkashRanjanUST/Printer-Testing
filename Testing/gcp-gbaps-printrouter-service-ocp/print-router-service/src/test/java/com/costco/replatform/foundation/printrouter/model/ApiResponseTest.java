package com.costco.replatform.foundation.printrouter.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiResponseTest {

    @Nested
    @DisplayName("Static factory methods")
    class Factories {

        @Test
        @DisplayName("ok(response) -> success with the default processed message")
        void ok_withResponse() {
            ApiResponse<String> result = ApiResponse.ok("payload");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getResponse()).isEqualTo("payload");
            assertThat(result.getMessage()).isEqualTo("The request has been processed");
            assertThat(result.getErrorCode()).isNull();
            assertThat(result.getErrorStatus()).isNull();
        }

        @Test
        @DisplayName("ok(response, message) -> success with a custom message")
        void ok_withResponseAndMessage() {
            ApiResponse<Integer> result = ApiResponse.ok(42, "Custom message");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getResponse()).isEqualTo(42);
            assertThat(result.getMessage()).isEqualTo("Custom message");
        }

        @Test
        @DisplayName("init(response) -> success with the initiated message")
        void init_withResponse() {
            ApiResponse<String> result = ApiResponse.init("job-1");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getResponse()).isEqualTo("job-1");
            assertThat(result.getMessage()).isEqualTo("The request has been initiated.");
        }

        @Test
        @DisplayName("error(message) -> failure with no payload")
        void error_withMessage() {
            ApiResponse<Object> result = ApiResponse.error("Something went wrong");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getResponse()).isNull();
            assertThat(result.getMessage()).isEqualTo("Something went wrong");
            assertThat(result.getErrorCode()).isNull();
            assertThat(result.getErrorStatus()).isNull();
        }

        @Test
        @DisplayName("failures(id, message, status) -> failure with error code and status as text")
        void failures_withAllFields() {
            ApiResponse<Object> result = ApiResponse.failures("ERR_001", "Bad request", 400);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getResponse()).isNull();
            assertThat(result.getMessage()).isEqualTo("Bad request");
            assertThat(result.getErrorCode()).isEqualTo("ERR_001");
            assertThat(result.getErrorStatus()).isEqualTo("400");
        }

        @Test
        @DisplayName("failures(...) with a null status currently throws NullPointerException")
        void failures_withNullStatus_throwsNpe() {
            assertThatThrownBy(() -> ApiResponse.failures("ERR_001", "Bad request", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Constructors")
    class Constructors {

        @Test
        @DisplayName("3-arg constructor sets success, response and message only")
        void threeArgConstructor() {
            ApiResponse<String> result = new ApiResponse<>(true, "data", "desc");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getResponse()).isEqualTo("data");
            assertThat(result.getMessage()).isEqualTo("desc");
            assertThat(result.getErrorCode()).isNull();
            assertThat(result.getErrorStatus()).isNull();
        }

        @Test
        @DisplayName("5-arg constructor sets every field")
        void fiveArgConstructor() {
            ApiResponse<String> result =
                    new ApiResponse<>(false, "data", "desc", "ERR_030", "502");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getResponse()).isEqualTo("data");
            assertThat(result.getMessage()).isEqualTo("desc");
            assertThat(result.getErrorCode()).isEqualTo("ERR_030");
            assertThat(result.getErrorStatus()).isEqualTo("502");
        }

        @Test
        @DisplayName("A null description overrides the 'Request not processed' default")
        void nullDescription_overridesDefaultMessage() {
            ApiResponse<String> result = new ApiResponse<>(true, null, null);

            assertThat(result.getMessage()).isNull();
        }
    }

    @Test
    @DisplayName("Setters and getters round-trip every field")
    void settersAndGetters_roundTrip() {
        ApiResponse<String> result = new ApiResponse<>(false, null, "initial");

        result.setSuccess(true);
        result.setResponse("updated");
        result.setMessage("updated message");
        result.setErrorCode("ERR_999");
        result.setErrorStatus("500");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResponse()).isEqualTo("updated");
        assertThat(result.getMessage()).isEqualTo("updated message");
        assertThat(result.getErrorCode()).isEqualTo("ERR_999");
        assertThat(result.getErrorStatus()).isEqualTo("500");
    }
}