package com.example.demo.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Сервис для работы с JWT токенами.
 * Извлекает информацию из токенов для использования в бизнес-логике.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * Извлекает токен из заголовка Authorization.
     * 
     * @param authHeader заголовок Authorization в формате "Bearer <token>"
     * @return токен или null, если заголовок невалидный
     */
    public String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    /**
     * Извлекает email из JWT токена.
     * 
     * @param token JWT токен
     * @return email из токена или null, если не удалось извлечь
     */
    public String extractEmailFromToken(String token) {
        try {
            SecretKey secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // Пробуем получить email из разных claims
            String email = claims.get("email", String.class);
            if (email == null || email.isBlank()) {
                // Если email нет, используем subject (обычно это login/email)
                email = claims.getSubject();
            }

            return email;
        } catch (Exception e) {
            log.warn("Failed to extract email from token: {}", e.getMessage());
            return null;
        }
    }
}

