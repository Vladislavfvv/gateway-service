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
        logger.debug("Request headers: {}", request.getHeaders());
        
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            logger.info("Response status: {}", exchange.getResponse().getStatusCode());
        }));
    }

    @Override
    public int getOrder() {
        return -1; // Выполняется первым
    }
}

