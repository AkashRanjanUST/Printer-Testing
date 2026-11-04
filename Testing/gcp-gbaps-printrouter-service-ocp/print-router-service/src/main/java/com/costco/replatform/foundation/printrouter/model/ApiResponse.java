package com.costco.replatform.foundation.printrouter.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ApiResponse<T> {

    @Schema(description = "Indicates whether the request was processed successfully", defaultValue = "false")
    private boolean success;

    @Schema(description = "Error status if the request fails", defaultValue = "")
    private String errorStatus;

    @Schema(description = "Application-specific error code", defaultValue = "")
    private String errorCode;

    @Schema(description = "Response payload. Null when the request fails or no data is available",
            nullable = true)
    private T response;

    @Schema(description = "Response message describing the outcome of the request",
            defaultValue = "Request not processed")
    private String message = "Request not processed";

    public ApiResponse(boolean success, T response, String description) {
        this.success = success;
        this.response = response;
        this.message = description;
    }
    public ApiResponse(boolean success, T response, String description,String errorCode,String errorStatus) {
        this.success = success;
        this.response = response;
        this.message = description;
        this.errorCode = errorCode;
        this.errorStatus = errorStatus;
    }

    public static <T> ApiResponse<T> ok(T response) {
        return new ApiResponse<>(true, response, "The request has been processed");
    }
    
    public static <T> ApiResponse<T> init(T response) {
        return new ApiResponse<>(true, response, "The request has been initiated.");
    }
    
    public static <T> ApiResponse<T> ok(T response, String message) {
        return new ApiResponse<>(true, response, message);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
    public static <T> ApiResponse<T> failures(String exceptionId,String message,Integer exceptionCode) {
        return new ApiResponse<>(false, null, message,exceptionId,exceptionCode.toString());
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public T getResponse() {
        return response;
    }

    public void setResponse(T response) {
        this.response = response;
    }


    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getErrorStatus() {
        return errorStatus;
    }

    public void setErrorStatus(String errorStatus) {
        this.errorStatus = errorStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
}
