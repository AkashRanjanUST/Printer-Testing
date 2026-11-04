package com.costco.replatform.foundation.printrouter.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @ParameterizedTest(name = "{0} has a well-formed code and a description")
    @EnumSource(ErrorCode.class)
    @DisplayName("Every ErrorCode has a code like ERR_### and a non-blank description")
    void everyErrorCode_hasValidCodeAndDescription(ErrorCode errorCode) {
        assertThat(errorCode.getCode()).matches("ERR_\\d{3}");
        assertThat(errorCode.getDescription()).isNotBlank();
    }

    @Test
    @DisplayName("All error codes are unique")
    void errorCodes_areUnique() {
        long distinctCodes = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .distinct()
                .count();

        assertThat(distinctCodes).isEqualTo(ErrorCode.values().length);
    }

    @Test
    @DisplayName("INVALID_REQUEST maps to ERR_001 (used by PrinterConnectivityService failures)")
    void invalidRequest_hasExpectedCodeAndDescription() {
        assertThat(ErrorCode.INVALID_REQUEST.getCode()).isEqualTo("ERR_001");
        assertThat(ErrorCode.INVALID_REQUEST.getDescription())
                .isEqualTo("The request is invalid.");
    }

    @Test
    @DisplayName("INTERNAL_SERVER_ERROR is the ERR_999 fallback")
    void internalServerError_isFallbackCode() {
        assertThat(ErrorCode.INTERNAL_SERVER_ERROR.getCode()).isEqualTo("ERR_999");
    }
}