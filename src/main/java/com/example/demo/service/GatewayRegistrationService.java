package com.example.demo.service;

import com.example.demo.dto.CreateUserFromTokenRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.dto.RegisterResponse;
import com.example.demo.dto.TokenResponse;
import com.example.demo.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для кастомного flow регистрации пользователя.
 * Реализует бизнес-логику регистрации:
 * 1. Создание credentials в Authentication Service
 * 2. Создание профиля пользователя в User Service с rollback при ошибках
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayRegistrationService {

    private final WebClient webClient;
    private final JwtTokenService jwtTokenService;

    @Value("${services.auth.url}")
    private String authServiceBase;

    @Value("${services.user.url}")
    private String userServiceBase;

    @Value("${internal.api.key:}")
    private String internalApiKey;

    /**
     * Регистрирует credentials в Authentication Service.
     * 
     * @param request данные для регистрации (login, password, role)
     * @return RegisterResponse с сообщением об успешной регистрации
     */
    public Mono<RegisterResponse> registerCredentials(RegisterRequest request) {
        log.info("Registering credentials for user: {}", request.getLogin());
        // Создаем credentials в Authentication Service и возвращаем токен
        return createCredentialsInAuthService(request)
                .map(tokenResponse -> {
                    log.info("Credentials registered successfully for user: {}. User should login to get tokens.",
                            request.getLogin());
                    return RegisterResponse.success(request.getLogin());
                })
                .onErrorResume(error -> {
                    log.error("Failed to register credentials for user: {}", request.getLogin(), error);
                    return Mono.error(new RuntimeException(
                            "Registration failed: Could not create credentials. Error: " +
                                    error.getMessage(), error));
                });
    }

    /**
     * Создает профиль пользователя в User Service с поддержкой rollback.
     * 
     * @param request данные для создания профиля (firstName, lastName, birthDate)
     * @param authHeader заголовок Authorization с JWT токеном
     * @return UserDto с данными созданного пользователя
     */
    public Mono<UserDto> createUserProfile(CreateUserFromTokenRequest request, String authHeader) {
        // Извлекаем токен из заголовка
        String token = jwtTokenService.extractTokenFromHeader(authHeader);
        if (token == null) {
            return Mono.error(new RuntimeException("Invalid Authorization header"));
        }

        // Извлекаем email из JWT токена
        String email = jwtTokenService.extractEmailFromToken(token);
        if (email == null) {
            return Mono.error(new RuntimeException("Could not extract email from token"));
        }

        log.info("Creating user profile for email: {}", email);

        // Создание профиля пользователя в User Service с токеном
        return createUserProfileInUserService(email, request, token)
                .onErrorResume(userServiceError -> { // Если ошибка при создании пользователя в User Service, то выполняем rollback
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
                .doOnSuccess(userDto -> log.info("User profile created successfully for user: {}", userDto.getEmail()));
    }

    /**
     * Создаёт credentials в Authentication Service.
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
                .bodyValue(authRequest) // Отправляем запрос на создание credentials в Authentication Service
                .retrieve() // Получаем ответ от Authentication Service
                .bodyToMono(TokenResponse.class) // Преобразуем ответ от Authentication Service в TokenResponse
                .doOnError(error -> log.error("Error calling Authentication Service: {}", error.getMessage()));
    }

    /**
     * Создаёт профиль пользователя в User Service.
     * Использует endpoint /api/v1/users/createUser, который принимает токен в заголовке Authorization.
     */
    private Mono<UserDto> createUserProfileInUserService(
            String email,
            CreateUserFromTokenRequest request,
            String token) {

        log.debug("Calling User Service to create user profile: {}", userServiceBase + "/api/v1/users/createUser");

        return webClient.post()
                .uri(userServiceBase + "/api/v1/users/createUser")
                .header("Authorization", "Bearer " + token) // Добавляем токен в заголовок запроса
                .bodyValue(request) // Отправляем запрос на создание пользователя в User Service
                .retrieve() // Получаем ответ от User Service
                .bodyToMono(UserDto.class) // Преобразуем ответ от User Service в UserDto
                .doOnError(error -> log.error("Error calling User Service: {}", error.getMessage()));
    }

    /**
     * Rollback: удаляет credentials из Authentication Service.
     */
    private Mono<Void> rollbackAuthServiceCredentials(String email) {
        log.warn("Rolling back credentials for user: {}", email);

        WebClient.RequestHeadersSpec<?> deleteRequest = webClient.delete() // Создаем запрос на удаление credentials из Authentication Service
                .uri(authServiceBase + "/auth/v1/internal/sync/users/{email}", email); // Устанавливаем URI запроса

        // Добавляем internal API key, если он настроен
        if (internalApiKey != null && !internalApiKey.isBlank()) { // Если internal API key настроен
            deleteRequest = deleteRequest.header("X-Internal-Api-Key", internalApiKey); // Добавляем internal API key в заголовок запроса
        }

        // Удаляем credentials из Authentication Service
        return deleteRequest
                .retrieve() // Отправляем запрос на удаление credentials из Authentication Service
                .toBodilessEntity() // Удаляем credentials из Authentication Service
                .then() // Возвращаем Mono<Void>
                .doOnSuccess(v -> log.info("Successfully rolled back credentials for user: {}", email))
                .doOnError(error -> log.error("Failed to rollback credentials for user: {}. Error: {}",
                        email, error.getMessage()))
                .onErrorResume(error -> {
                    // Логируем ошибку, но не прерываем цепочку
                    log.error("Rollback failed for user: {}, but continuing error propagation", email);
                    return Mono.empty();
                });
    }
}

