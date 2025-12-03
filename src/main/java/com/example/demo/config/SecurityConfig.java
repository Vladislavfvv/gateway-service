package com.example.demo.config;

import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import io.jsonwebtoken.security.Keys;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;
    /* JWT Decoder 
        Spring Security использует JWT Decoder для декодирования JWT токенов, полученных от authentication-service.
        JWT Decoder использует симметричный ключ (HS256) для проверки подлинности токенов.
    */
    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        // Используем симметричный ключ (HS256) для проверки JWT токенов от authentication-service
        SecretKey secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return NimbusReactiveJwtDecoder.withSecretKey(secretKey).build();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(ex -> ex
                        // Разрешаем доступ без JWT токена только для login и register
                        // Пути для проксирования к authentication-service
                        .pathMatchers("/auth/v1/login", "/auth/v1/register").permitAll()
                        // Путь для создания профиля пользователя (требует JWT токен)
                        .pathMatchers("/auth/v1/createUser").authenticated()
                        // Все остальные запросы требуют аутентификации (JWT токен)
                        .anyExchange().authenticated()
                )
                //Для запросов, требующих аутентификации, Gateway проверяет JWT токен из заголовка Authorization: Bearer <token>
                //Если токен валидный → запрос проходит дальше
                //Если токен невалидный или отсутствует → возвращается ошибка 401 Unauthorized
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtDecoder(jwtDecoder()))
                );

        return http.build();
    }
}

