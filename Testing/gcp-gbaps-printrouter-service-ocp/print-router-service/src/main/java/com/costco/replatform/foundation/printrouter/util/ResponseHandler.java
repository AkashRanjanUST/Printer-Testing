package com.costco.replatform.foundation.printrouter.util;

import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.costco.replatform.foundation.printrouter.model.PrinterIppResponse;
import com.costco.replatform.foundation.printrouter.model.PrinterIppResponse.ResponseData;


/**
 * Utility class for handling response building, error formatting, and status code resolution.
 * Centralizes all response construction logic that was previously scattered across controllers and services.
 */
@Component
public class ResponseHandler {

    private static final Logger logger = LoggerFactory.getLogger(ResponseHandler.class);

//    /**
//     * Builds error response for validation failures.
//     *
//     * @param trackingId tracking ID for logging
//     * @param errorMessage descriptive error message
//     * @return VendorCreateResponse with error data
//     */
//    public VendorCreateResponse buildValidationErrorResponse(String trackingId, String errorMessage) {
//        logger.warn("Building validation error response - trackingId: {}, message: {}", trackingId, errorMessage);
//        VendorCreateResponse response = new VendorCreateResponse();
//        ResponseData data = new ResponseData();
//        data.setOk(false);
//        data.setStatus(HttpStatus.BAD_REQUEST.value());
//        data.setMessage(errorMessage);
//        response.setData(data);
//        return response;
//    }

    /**
     * Builds error response for service failures.
     * Handles null response, null data, and non-ok responses with safe fallbacks.
     *
     * @param trackingId tracking ID for logging
     * @param serviceResponse the response from service (maybe null)
     * @return VendorCreateResponse with appropriate error data
     */
    public PrinterIppResponse buildServiceErrorResponse(String trackingId, PrinterIppResponse serviceResponse) {
        logger.warn("Building service error response - trackingId: {}", trackingId);

        // Handle null response
        if (serviceResponse == null) {
            logger.error("Service returned null response - trackingId: {}", trackingId);
            return buildErrorResponse(trackingId, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Internal server error while processing vendor creation");
        }

        // Handle null data
        if (serviceResponse.data() == null) {
            logger.error("Service response data is null - trackingId: {}", trackingId);
            return buildErrorResponse(trackingId, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Internal server error while processing vendor creation");
        }

        // Extract status and message from service response
        int status = resolveHttpStatus(serviceResponse.data().getStatus());
        String message = serviceResponse.data().getMessage() != null
                ? serviceResponse.data().getMessage()
                : "Service error occurred during vendor creation";

        return buildErrorResponse(trackingId, status, message);
    }

//    /**
//     * Builds a read endpoint error response for vendor copy/fetch failures.
//     *
//     * @param trackingId tracking ID for logging
//     * @param serviceResponse the response from service (maybe null)
//     * @return VendorCopyResponse with appropriate error data
//     */
//    public VendorCopyResponse buildReadErrorResponse(String trackingId, VendorCopyResponse serviceResponse) {
//        logger.warn("Building read error response - trackingId: {}", trackingId);
//
//        // Handle null response
//        if (serviceResponse == null) {
//            logger.error("Service returned null response - trackingId: {}", trackingId);
//            return buildCopyErrorResponse(trackingId, HttpStatus.INTERNAL_SERVER_ERROR.value(),
//                    "Internal server error while fetching vendor");
//        }
//
//        // Handle null data
//        if (serviceResponse.getData() == null) {
//            logger.error("Service response data is null - trackingId: {}", trackingId);
//            return buildCopyErrorResponse(trackingId, HttpStatus.INTERNAL_SERVER_ERROR.value(),
//                    "Internal server error while fetching vendor");
//        }
//
//        // Extract status and message from service response
//        int status = resolveHttpStatus(serviceResponse.getData().getStatus());
//        String message = serviceResponse.getData().getMessage() != null
//                ? serviceResponse.getData().getMessage()
//                : "Service error occurred during vendor fetch";
//
//        return buildCopyErrorResponse(trackingId, status, message);
//    }

    /**
     * Resolves the HTTP status code from service status integer.
     * Maps service status codes to appropriate HTTP status codes with fallback to 500.
     *
     * @param serviceStatus the status code from service response
     * @return appropriate HTTP status code
     */
    public int resolveHttpStatus(int serviceStatus) {
        // If service provides a valid HTTP status code (>0), use it
        if (serviceStatus > 0) {
            return serviceStatus;
        }
        // Fallback to internal server error
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    /**
     * Private helper to build error response for create operations.
     */
    public PrinterIppResponse buildErrorResponse(String trackingId, int status, String message) {    	
        ResponseData data = new ResponseData();
        data.setOk(false);
        data.setStatus(status);
        data.setMessage(message);
        int requestId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        PrinterIppResponse response = new PrinterIppResponse(0, requestId, 0, "IPP request is Success",true,data);
        logger.info("Success response built - trackingId: {}, status: {}", trackingId, status);
        return response;
    }
    
    /**
     * Private helper to build error response for create operations.
     */
    private PrinterIppResponse buildSuccessResponse(String trackingId, int status, String message) {    	
        ResponseData data = new ResponseData();
        data.setOk(true);
        data.setStatus(status);
        data.setMessage(message);
        int requestId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        PrinterIppResponse response = new PrinterIppResponse(0, requestId, 0, "IPP request interrupted",false,data);
        logger.info("Error response built - trackingId: {}, status: {}", trackingId, status);
        return response;
    }

//    /**
//     * Private helper to build error response for read operations.
//     */
//    private VendorCopyResponse buildCopyErrorResponse(String trackingId, int status, String message) {
//        VendorCopyResponse response = new VendorCopyResponse();
//        VendorCopyResponse.Data data = new VendorCopyResponse.Data();
//        data.setOk(false);
//        data.setStatus(status);
//        data.setMessage(message);
//        response.setData(data);
//        logger.info("Copy error response built - trackingId: {}, status: {}", trackingId, status);
//        return response;
//    }
//
//    /**
//     * Checks if a response indicates success.
//     *
//     * @param response the response to check
//     * @return true if response is non-null, has data, and data.isOk() is true
//     */
//    public boolean isSuccessResponse(VendorCreateResponse response) {
//        return response != null && response.getData() != null && response.getData().isOk();
//    }
//
//    /**
//     * Checks if a read response indicates success.
//     *
//     * @param response the response to check
//     * @return true if response is non-null, has data, and data.isOk() is true
//     */
//    public boolean isSuccessCopyResponse(VendorCopyResponse response) {
//        return response != null && response.getData() != null && response.getData().isOk();
//    }
}
