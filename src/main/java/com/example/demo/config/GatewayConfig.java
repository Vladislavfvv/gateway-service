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
        
        logger.debug("=== GATEWAY REQUEST ===");
        logger.debug("Method: {}, Path: {}", method, path);
        logger.debug("Full URI: {}", request.getURI());
        
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
        
        // CORS заголовки обрабатываются через CorsWebFilter и corsPreflightFilter
        // GatewayConfig выполняется после CORS фильтров, поэтому не добавляем заголовки здесь
        // чтобы не конфликтовать с CorsWebFilter
        
        return chain.filter(exchange)
            .doOnSuccess(result -> {
                long duration = System.currentTimeMillis() - startTime;
                // Логируем только медленные запросы (>1 секунды) или ошибки
                if (duration > 1000) {
                    logger.warn("=== GATEWAY RESPONSE (SLOW) ===");
                    logger.warn("Path: {}, Status: {}, Duration: {}ms", 
                        path, 
                        exchange.getResponse().getStatusCode(),
                        duration);
                } else {
                    logger.debug("=== GATEWAY RESPONSE ===");
                    logger.debug("Path: {}, Status: {}, Duration: {}ms", 
                        path, 
                        exchange.getResponse().getStatusCode(),
                        duration);
                }
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