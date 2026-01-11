package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Arrays;

@Configuration
public class CorsConfig {

    private static final Logger logger = LoggerFactory.getLogger(CorsConfig.class);

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        
        // Используем setAllowedOriginPatterns вместо setAllowedOrigins для работы с setAllowCredentials(true)
        // Это позволяет использовать wildcards и работает с credentials
        corsConfig.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:3000",
            "http://127.0.0.1:3000"
        ));
        
        // Разрешаем все методы HTTP
        corsConfig.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));
        
        // Разрешаем все заголовки (используем * для разрешения всех)
        corsConfig.addAllowedHeader("*");
        
        // Разрешаем отправку credentials (cookies, authorization headers)
        corsConfig.setAllowCredentials(true);
        
        // Заголовки, которые клиент может читать
        corsConfig.setExposedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type"
        ));
        
        // Время кэширования preflight запросов
        corsConfig.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        
        logger.info("CORS configured: allowed origins = http://localhost:3000, http://127.0.0.1:3000");
        
        return new CorsWebFilter(source);
    }
    
    /**
     * Дополнительный фильтр для гарантированного добавления CORS заголовков ко всем ответам
     * Выполняется после CorsWebFilter, чтобы убедиться, что заголовки присутствуют
     * Использует ServerHttpResponseDecorator для гарантированного добавления заголовков
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    public WebFilter ensureCorsHeadersFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            var request = exchange.getRequest();
            String origin = request.getHeaders().getFirst("Origin");
            
            // Если Origin присутствует и это localhost:3000, оборачиваем ответ
            if (origin != null && (origin.contains("localhost:3000") || origin.contains("127.0.0.1:3000"))) {
                var originalResponse = exchange.getResponse();
                var decoratedResponse = new org.springframework.http.server.reactive.ServerHttpResponseDecorator(originalResponse) {
                    @Override
                    public org.springframework.http.HttpHeaders getHeaders() {
                        var headers = super.getHeaders();
                        
                        // Гарантируем наличие CORS заголовков
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
                        
                        return headers;
                    }
                };
                
                return chain.filter(exchange.mutate().response(decoratedResponse).build());
            }
            
            return chain.filter(exchange);
        };
    }
}

