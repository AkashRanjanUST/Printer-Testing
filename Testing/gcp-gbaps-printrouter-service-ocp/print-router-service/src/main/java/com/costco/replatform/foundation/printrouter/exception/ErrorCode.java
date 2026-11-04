package com.costco.replatform.foundation.printrouter.exception;


public enum ErrorCode {

    // ────────────── Validation Errors ──────────────
    INVALID_REQUEST("ERR_001", "The request is invalid."),
    MISSING_REQUIRED_FIELD("ERR_002", "A required field is missing."),
    INVALID_INPUT_FORMAT("ERR_003", "The input format is invalid."),
    RESOURCE_NOT_FOUND("ERR_004", "The requested resource not found."),

    // ────────────── Business Errors ──────────────
    PRINTER_NOT_FOUND("ERR_012", "The specified vendor was not found."),
    PRINTER_INACTIVE("ERR_013", "The specified vendor is inactive."),
    // ────────────── Authorization / Security ──────────────
    UNAUTHORIZED("ERR_020", "Unauthorized access."),
    FORBIDDEN("ERR_021", "Access to the requested resource is forbidden."),

    // ────────────── System / Integration Errors ──────────────
    DOWNSTREAM_SERVICE_FAILURE("ERR_030", "A downstream service failed to respond."),
    CIRCUIT_BREAKER_OPEN("ERR_031", "The request was short-circuited due to system load."),
    TIMEOUT("ERR_032", "The request timed out while waiting for a response."),
    RETRY_FAILURE("ERR_033", "The Retry Failed after configured count of attempt"),
    SERVICE_UNAVAILABLE("ERR_034", "The requested service is Unavailable at this moment."),
    RATE_LIMITER_EXCEED("ERR_035", "The rate limiter has exceed the configured count in the given time frame."),
    FEATURE_NOT_SUPPORTED("ERR_036", "The requested feature is not supported."),
    DATA_INTEGRITY_VIOLATION("ERR_037","The Data Base Constraint or Integrity violation."),
    CONSTRAINT_VIOLATION("ERR_038","The Bean validation or constraint validation failure."),
    TRANSACTION_SYSTEM_ERROR("ERR_039","Transaction commit or rollback failure."),
    
    ENTITY_ALREADY_EXISTS("ERR_040","The Entity already exists in persistence store."),
    ILLEGAL_ARGUMENT("ERR_041","The argument passed to service or API is Invalid."),
    PERSISTENCE_ERROR("ERR_042","The Generic JPA persistence exception."),
    DUPLICATE_RESOURCE("ERR_043","The Duplicate Resource is exists."),

    // ────────────── Generic / Fallback ──────────────
    INTERNAL_SERVER_ERROR("ERR_999", "An unexpected error occurred."),
	
	//Non-Exception/ Business validation result. 
	SERVICE_RESULT("ERR_050", "Business Specific result with Http Status 200.");

    private final String code;
    private final String description;

    ErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
