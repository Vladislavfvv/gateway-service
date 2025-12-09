package com.example.demo.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Универсальное исключение для ошибок от downstream-сервисов (auth, user, order и т.д.).
 * Содержит HTTP-статус и «сырое» тело ответа, чтобы gateway мог корректно его вернуть клиенту.
 */
@Getter
public class DownstreamServiceException  extends RuntimeException{
    private final HttpStatus status;
    private final String body;
    private final String contentType;

    public DownstreamServiceException(HttpStatus status, String body, String contentType) {
        super(body);
        this.status = status;
        this.body = body;
        this.contentType = contentType;
    }

    public DownstreamServiceException(WebClientResponseException ex) {
        super(ex.getMessage(), ex);
        this.status = (HttpStatus) ex.getStatusCode();
        this.body = ex.getResponseBodyAsString();
        MediaType mediaType = ex.getHeaders().getContentType();
        this.contentType = mediaType != null ? mediaType.toString() : null;
    }
}
