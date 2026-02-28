package com.sorted.common.helper;

import com.sorted.common.enums.ResponseCode;
import com.sorted.common.exceptions.BadRequestException;
import com.sorted.common.exceptions.BaseException;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

@Log4j2
@ControllerAdvice
public class CustomExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Object> handleCustomIllegalArgumentsException(BaseException e) {
        log.error("CustomIllegalArgumentsException caught - Message: {}, ResponseCode: {}, HttpStatus: {}",
                e.getMessage(), e.getResponseCode(), e.getHttpStatus());

        HttpStatus status = null == e.getHttpStatus() ? HttpStatus.NOT_ACCEPTABLE : e.getHttpStatus();
        if (e.getResponseCode() == ResponseCode.ACCESS_DENIED) {
            status = HttpStatus.UNAUTHORIZED;
            log.warn("Access denied - changing status to UNAUTHORIZED");
        }
        String errMessage = e.getMessage() == null
                ? "Illegal argument exception, please check your request parameters and body"
                : e.getMessage();
        String userMessage = e.getUserMessage() == null ? errMessage : e.getUserMessage();

        log.debug("Building response - Status: {}, ErrorMessage: {}, UserMessage: {}, MessageCode: {}",
                status, errMessage, userMessage, e.getMessageCode());

        SEResponse apiResponse = SEResponse.builder().status(status).responseCode(e.getMessageCode())
                .errorMessage(errMessage).userMessage(userMessage).build();

        log.info("Returning CustomIllegalArgumentsException response with status: {}", status);
        return new ResponseEntity<Object>(apiResponse, new HttpHeaders(), status);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<String> handleBadRequestException(BadRequestException ex) {
        log.error("BadRequestException caught - Message: {}", ex.getMessage());
        log.debug("Stack trace: ", ex);

        log.info("Returning BadRequestException response with status: BAD_REQUEST (400)");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException caught - Status: {}, Reason: {}, Message: {}",
                ex.getStatusCode(), ex.getReason(), ex.getMessage());
        log.debug("Stack trace: ", ex);

        log.warn("Returning ResponseStatusException response with status: INTERNAL_SERVER_ERROR (500)");
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ex.getMessage());
    }

}
