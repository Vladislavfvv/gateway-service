package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter для удаления дублирующихся CORS заголовков от downstream сервисов.
 * Выполняется после CorsWebFilter, чтобы удалить заголовки, которые были добавлены
 * downstream сервисами, но оставить те, которые добавил CorsWebFilter.
 * Order = Ordered.HIGHEST_PRECEDENCE + 1 (выполняется после CorsWebFilter)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RemoveCorsHeadersWebFilter implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(RemoveCorsHeadersWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange)
            .doOnSuccess(result -> {
                // После того, как CorsWebFilter добавил свои заголовки,
                // проверяем, нет ли дублирующихся значений
                ServerHttpResponse response = exchange.getResponse();
                
                // Проверяем Access-Control-Allow-Origin на дублирование
                var origins = response.getHeaders().get("Access-Control-Allow-Origin");
                if (origins != null && origins.size() > 1) {
                    logger.warn("Multiple Access-Control-Allow-Origin headers found, removing duplicates");
                    // Оставляем только последнее значение (от CorsWebFilter)
                    String lastOrigin = origins.get(origins.size() - 1);
                    response.getHeaders().set("Access-Control-Allow-Origin", lastOrigin);
                }
                
                // Аналогично для других заголовков
                var credentials = response.getHeaders().get("Access-Control-Allow-Credentials");
                if (credentials != null && credentials.size() > 1) {
                    String lastCredential = credentials.get(credentials.size() - 1);
                    response.getHeaders().set("Access-Control-Allow-Credentials", lastCredential);
                }
                
                var methods = response.getHeaders().get("Access-Control-Allow-Methods");
                if (methods != null && methods.size() > 1) {
                    String lastMethod = methods.get(methods.size() - 1);
                    response.getHeaders().set("Access-Control-Allow-Methods", lastMethod);
                }
                
                var headers = response.getHeaders().get("Access-Control-Allow-Headers");
                if (headers != null && headers.size() > 1) {
                    String lastHeader = headers.get(headers.size() - 1);
                    response.getHeaders().set("Access-Control-Allow-Headers", lastHeader);
                }
            });
    }
}

