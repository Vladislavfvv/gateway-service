package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
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
        logger.info("Incoming request: {} {}", request.getMethod(), request.getURI());
        
        // Логируем наличие Authorization заголовка для отладки
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null) {
            logger.debug("Authorization header present: {}", authHeader.substring(0, Math.min(20, authHeader.length())) + "...");
        } else {
            logger.debug("No Authorization header in request");
        }
        
        // Spring Cloud Gateway автоматически передает все заголовки, включая Authorization
        // Но убеждаемся, что заголовок будет передан дальше
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            logger.info("Response status: {}", exchange.getResponse().getStatusCode());
        }));
    }

    @Override
    public int getOrder() {
        return -1; // Выполняется первым
    }
}

