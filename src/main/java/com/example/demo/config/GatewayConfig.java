package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayConfig implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(GatewayConfig.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().toString();
        
        logger.info("=== GATEWAY REQUEST ===");
        logger.info("Method: {}, Path: {}", method, path);
        logger.info("Full URI: {}", request.getURI());
        
        String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);
        if (origin != null) {
            logger.debug("Origin: {}", origin);
        }
        
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null) {
            logger.debug("Authorization header present: {}...", authHeader.substring(0, Math.min(20, authHeader.length())));
        } else {
            logger.debug("No Authorization header in request");
        }
        
        long startTime = System.currentTimeMillis();
        
        // Оборачиваем ответ для гарантированного добавления CORS заголовков
        var originalResponse = exchange.getResponse();
        var decoratedResponse = new org.springframework.http.server.reactive.ServerHttpResponseDecorator(originalResponse) {
            @Override
            public org.springframework.http.HttpHeaders getHeaders() {
                var headers = super.getHeaders();
                
                // Добавляем CORS заголовки, если Origin присутствует и это localhost:3000
                if (origin != null && (origin.contains("localhost:3000") || origin.contains("127.0.0.1:3000"))) {
                    if (!headers.containsKey("Access-Control-Allow-Origin")) {
                        logger.debug("Adding Access-Control-Allow-Origin: {}", origin);
                        headers.add("Access-Control-Allow-Origin", origin);
                    }
                    if (!headers.containsKey("Access-Control-Allow-Credentials")) {
                        headers.add("Access-Control-Allow-Credentials", "true");
                    }
                    if (!headers.containsKey("Access-Control-Allow-Methods")) {
                        headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
                    }
                    if (!headers.containsKey("Access-Control-Allow-Headers")) {
                        headers.add("Access-Control-Allow-Headers", "*");
                    }
                }
                
                return headers;
            }
        };
        
        return chain.filter(exchange.mutate().response(decoratedResponse).build())
            .doOnSuccess(result -> {
                long duration = System.currentTimeMillis() - startTime;
                logger.info("=== GATEWAY RESPONSE ===");
                logger.info("Path: {}, Status: {}, Duration: {}ms", 
                    path, 
                    exchange.getResponse().getStatusCode(),
                    duration);
            })
        
            .doOnError(error -> {
                long duration = System.currentTimeMillis() - startTime;
                logger.error("=== GATEWAY ERROR ===");
                logger.error("Path: {}, Error: {}, Duration: {}ms", 
                    path, 
                    error.getMessage(),
                    duration);
                logger.error("Error class: {}", error.getClass().getName());
                if (error.getCause() != null) {
                    logger.error("Cause: {}", error.getCause().getMessage());
                }
            });
    }

    @Override
    public int getOrder() {
        return -1;
    }
}