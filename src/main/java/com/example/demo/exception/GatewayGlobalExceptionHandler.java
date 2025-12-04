package com.example.demo.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Глобальный обработчик ошибок для gateway-service.
 */
@RestControllerAdvice
public class GatewayGlobalExceptionHandler {

    /**
     * Унифицированная обработка ошибок от downstream-сервисов (auth, user, order и т.д.).
     * Gateway не интерпретирует бизнес-ошибки, а возвращает тот же статус и тело,
     * которое сформировал конкретный сервис (через свой GlobalExceptionHandler).
     */
    @ExceptionHandler(DownstreamServiceException.class)
    public ResponseEntity<String> handleDownstreamServiceException(DownstreamServiceException ex) {
        HttpStatus status = ex.getStatus();
        HttpHeaders headers = new HttpHeaders();
        if (ex.getContentType() != null) {
            headers.setContentType(MediaType.parseMediaType(ex.getContentType()));
        } else {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        String body = ex.getBody() != null ? ex.getBody() : "";
        return new ResponseEntity<>(body, headers, status);
    }
}


