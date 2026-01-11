package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Фильтр для удаления CORS заголовков от downstream сервисов.
 * Выполняется после получения ответа от downstream сервиса (NettyRoutingFilter),
 * но перед отправкой ответа клиенту.
 * Order = -2 (выполняется раньше GatewayConfig, чтобы удалить заголовки до логирования)
 */
@Component
public class RemoveCorsHeadersFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(RemoveCorsHeadersFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                // Удаляем все CORS заголовки от downstream сервисов перед отправкой
                removeCorsHeaders();
                logger.debug("Removed CORS headers from downstream service response");
                return super.writeWith(body);
            }
            
            private void removeCorsHeaders() {
                // Удаляем все возможные CORS заголовки
                getDelegate().getHeaders().remove("Access-Control-Allow-Origin");
                getDelegate().getHeaders().remove("Access-Control-Allow-Credentials");
                getDelegate().getHeaders().remove("Access-Control-Allow-Methods");
                getDelegate().getHeaders().remove("Access-Control-Allow-Headers");
                getDelegate().getHeaders().remove("Access-Control-Expose-Headers");
                getDelegate().getHeaders().remove("Access-Control-Max-Age");
                // Также удаляем Vary, так как он может содержать информацию о CORS
                // Но только если он содержит Origin
                String vary = getDelegate().getHeaders().getFirst("Vary");
                if (vary != null && vary.contains("Origin")) {
                    // Удаляем Vary только если он связан с CORS
                    getDelegate().getHeaders().remove("Vary");
                }
            }
        };
        
        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    @Override
    public int getOrder() {
        // Выполняем раньше GatewayConfig (order=-1), чтобы удалить заголовки до логирования
        // Но после получения ответа от downstream сервиса
        // CorsWebFilter выполняется как WebFilter, а не как GatewayFilter, поэтому его порядок другой
        return -2;
    }
}

