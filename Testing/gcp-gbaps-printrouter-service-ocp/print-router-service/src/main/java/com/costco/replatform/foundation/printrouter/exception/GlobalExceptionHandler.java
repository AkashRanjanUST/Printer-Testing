package com.costco.replatform.foundation.printrouter.exception;



import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.costco.replatform.foundation.printrouter.model.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @Autowired
    private MessageSource messageSource;

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  
    /**
     * Handles validation failures triggered by
     * {@link MethodArgumentNotValidException}.
     * <p>
     * This exception is raised when request payload validation (e.g.
     * {@code @Valid}) fails due to constraint violations on request fields. The
     * handler extracts field-level validation errors and returns them as a map of
     * field names to localized validation messages.
     * </p>
     *
     //	 * @param ex the validation exception containing binding and field error details
     * @return a {@link ResponseEntity} with a map of field names to validation
     *         messages and HTTP status {@code 400 BAD_REQUEST}
     */

    private String getRequestInfo(HttpServletRequest request) {
        if (request == null) return "Unknown Request";
        return String.format("%s %s", request.getMethod(), request.getRequestURI());
    }

    
    /**
     * Method to throw exception for Method Argument Invalid.
     * @param ex
     * @return
     */
    @ExceptionHandler({MethodArgumentNotValidException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        logger.warn("GlobalExceptionHandler:handleValidationExceptions", ex);
        Map<String, String> errors = new HashMap<>();
        if (ex != null && ex.getBindingResult() != null && !ex.getBindingResult().getFieldErrors().isEmpty()) {
            ex.getBindingResult().getFieldErrors()
                    .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failures(ErrorCode.INVALID_REQUEST.getCode(), errors.toString(),400));
    }
    
    
    /**
     * Method to throw exception for Illegal argument.
     * @param ex
     * @return
     */
    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgExceptions(IllegalArgumentException ex) {
        logger.warn("GlobalExceptionHandler:handleIllegalArgExceptions", ex);       
        	 return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        	            .body(ApiResponse.failures(
        	                    ErrorCode.INVALID_REQUEST.getCode(),
        	                    ex!=null?ex.getMessage():"Illegal Argument Exception.",
        	                    HttpStatus.BAD_REQUEST.value()));
       
    }
    
}
