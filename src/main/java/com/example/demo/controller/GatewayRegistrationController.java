package com.example.demo.controller;

import com.example.demo.dto.CreateUserFromTokenRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.dto.RegisterResponse;
import com.example.demo.dto.TokenResponse;
import com.example.demo.dto.UserDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Контроллер для кастомного flow регистрации пользователя.
 * Реализует трехэтапную регистрацию:
 * 1. Регистрация credentials в Authentication Service (POST /auth/register) - только login, password, role
 * 2. Логин для получения токенов (POST /auth/v1/login) - проксируется к authentication-service
 * 3. Создание профиля пользователя в User Service (POST /auth/createUser) - firstName, lastName, birthDate
 * С поддержкой rollback при ошибках на этапе создания профиля.
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class GatewayRegistrationController {
    private final WebClient webClient;
    
    @Value("${services.auth.url}")
    private String authServiceBase;
    
    @Value("${services.user.url}")
    private String userServiceBase;
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${internal.api.key:}")
    private String internalApiKey;

    /**
     * Шаг 1: Регистрация credentials в Authentication Service.
     * Принимает только login, password, role.
     * Создает credentials, но НЕ выдает токены.
     * Токены выдаются при логине (POST /auth/v1/login).
     * 
     * @param req данные для регистрации (login, password, role)
     * @return ResponseEntity с сообщением об успешной регистрации
     */
    @PostMapping("/register")
    public Mono<ResponseEntity<RegisterResponse>> register(@Valid @RequestBody RegisterRequest req) {
        log.info("Registering credentials for user: {}", req.getLogin());
        
        // Создаем credentials в Authentication Service
        // Примечание: authentication-service возвращает токены, но мы их игнорируем
        // и возвращаем только сообщение об успехе
        return createCredentialsInAuthService(req)
                .map(tokenResponse -> {
                    log.info("Credentials registered successfully for user: {}. User should login to get tokens.", 
                            req.getLogin());
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(RegisterResponse.success(req.getLogin()));
                })
                .onErrorResume(error -> {
                    log.error("Failed to register credentials for user: {}", req.getLogin(), error);
                    return Mono.error(new RuntimeException(
                            "Registration failed: Could not create credentials. Error: " + 
                            error.getMessage(), error));
                });
    }

    /**
     * Шаг 3: Создание профиля пользователя в User Service.
     * Использует JWT токен из заголовка Authorization для получения email.
     * Токен должен быть получен через логин (POST /auth/v1/login).
     * Принимает firstName, lastName, birthDate.
     * Выполняет rollback (удаляет credentials), если создание профиля не удалось.
     * 
     * @param request данные для создания профиля (firstName, lastName, birthDate)
     * @param authHeader заголовок Authorization с JWT токеном (Bearer <token>)
     * @return ResponseEntity с данными созданного пользователя или ошибкой
     */
    @PostMapping("/createUser")
    public Mono<ResponseEntity<?>> createUser(
            @Valid @RequestBody CreateUserFromTokenRequest request,
            @RequestHeader("Authorization") String authHeader) {
        
        // Извлекаем токен из заголовка
        String token = extractTokenFromHeader(authHeader);
        if (token == null) {
            return Mono.error(new RuntimeException("Invalid Authorization header"));
        }
        
        // Извлекаем email из JWT токена
        String email = extractEmailFromToken(token);
        if (email == null) {
            return Mono.error(new RuntimeException("Could not extract email from token"));
        }
        
        log.info("Creating user profile for email: {}", email);
        
        // Создание профиля пользователя в User Service с токеном
        return createUserProfileInUserService(email, request, token)
                .onErrorResume(userServiceError -> {
                    log.error("Failed to create user profile in User Service for user: {}. Initiating rollback...", 
                            email, userServiceError);
                    
                    // Rollback: удаляем credentials из Authentication Service
                    return rollbackAuthServiceCredentials(email)
                            .then(Mono.error(new RuntimeException(
                                    "User profile creation failed. " +
                                    "Credentials have been rolled back. Error: " + 
                                    userServiceError.getMessage(), 
                                    userServiceError)));
                })
                .map(userDto -> {
                    log.info("User profile created successfully for user: {}", userDto.getEmail());
                    return ResponseEntity.status(HttpStatus.CREATED).body(userDto);
                });
    }

    /**
     * Создаёт credentials в Authentication Service
     */
    private Mono<TokenResponse> createCredentialsInAuthService(RegisterRequest req) {
        // Подготавливаем запрос для authentication-service (только login, password, role)
        Map<String, Object> authRequest = new HashMap<>();
        authRequest.put("login", req.getLogin());
        authRequest.put("password", req.getPassword());
        authRequest.put("role", req.getRole() != null ? req.getRole() : "ROLE_USER");
        
        log.debug("Calling Authentication Service to create credentials: {}", authServiceBase + "/auth/v1/register");
        
        return webClient.post()
                .uri(authServiceBase + "/auth/v1/register")
                .bodyValue(authRequest)
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .doOnError(error -> log.error("Error calling Authentication Service: {}", error.getMessage()));
    }

    /**
     * Создаёт профиль пользователя в User Service
     * Использует endpoint /api/v1/users/createUser, который принимает токен в заголовке Authorization
     */
    private Mono<UserDto> createUserProfileInUserService(
            String email, 
            CreateUserFromTokenRequest request, 
            String token) {
        
        log.debug("Calling User Service to create user profile: {}", userServiceBase + "/api/v1/users/createUser");
        
        return webClient.post()
                .uri(userServiceBase + "/api/v1/users/createUser")
                .header("Authorization", "Bearer " + token)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(UserDto.class)
                .doOnError(error -> log.error("Error calling User Service: {}", error.getMessage()));
    }

    /**
     * Rollback: удаляет credentials из Authentication Service
     */
    private Mono<Void> rollbackAuthServiceCredentials(String email) {
        log.warn("Rolling back credentials for user: {}", email);
        
        WebClient.RequestHeadersSpec<?> deleteRequest = webClient.delete()
                .uri(authServiceBase + "/auth/v1/internal/sync/users/{email}", email);
        
        // Добавляем internal API key, если он настроен
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            deleteRequest = deleteRequest.header("X-Internal-Api-Key", internalApiKey);
        }
        
        return deleteRequest
                .retrieve()
                .toBodilessEntity()
                .then()
                .doOnSuccess(v -> log.info("Successfully rolled back credentials for user: {}", email))
                .doOnError(error -> log.error("Failed to rollback credentials for user: {}. Error: {}", 
                        email, error.getMessage()))
                .onErrorResume(error -> {
                    // Логируем ошибку, но не прерываем цепочку
                    log.error("Rollback failed for user: {}, but continuing error propagation", email);
                    return Mono.empty();
                });
    }

    /**
     * Извлекает токен из заголовка Authorization
     */
    private String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    /**
     * Извлекает email из JWT токена
     */
    private String extractEmailFromToken(String token) {
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
