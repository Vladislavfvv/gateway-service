package com.example.demo.service;

import com.example.demo.dto.CreateUserFromTokenRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.dto.RegisterResponse;
import com.example.demo.dto.TokenResponse;
import com.example.demo.dto.UserDto;
import com.example.demo.exception.DownstreamServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Сервис для регистрации пользователя с использованием Saga паттерна (Orchestration).
 * Gateway выступает оркестратором распределенной транзакции:
 * 1. Создание credentials в Authentication Service
 * 2. Логин для получения токена (если нужно создать профиль)
 * 3. Создание профиля в User Service (если указаны данные профиля)
 * 
 * При ошибках выполняется компенсирующая транзакция (rollback).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayRegistrationService {

    private final WebClient webClient; // WebClient для выполнения запросов
    private final JwtTokenService jwtTokenService; // Сервис для работы с JWT токенами

    @Value("${services.auth.url}")
    private String authServiceBase;

    @Value("${services.user.url}") 
    private String userServiceBase; // URL сервиса пользователей

    @Value("${internal.api.key:}")
    private String internalApiKey; // ключ для внутренних запросов 

    /**
     * Главный метод регистрации пользователя (Saga Orchestrator).
     * Выполняет полный цикл регистрации с поддержкой rollback.
     * 
     * @param request данные для регистрации (credentials + опционально профиль)
     * @return RegisterResponse с результатом регистрации
     */
    public Mono<RegisterResponse> registerUser(RegisterRequest request) {
        log.info("Starting user registration: {}", request.getLogin());
        
        // ========== ШАГ 1: Подготовка ==========
        // Создаем Mono для credentials (это еще НЕ выполнение запроса, а только описание)
        // Запрос выполнится только когда на Mono подпишутся (subscribe)
        // 
        // КТО ПОДПИСЫВАЕТСЯ?
        // Spring WebFlux автоматически подписывается на Mono когда:
        // 1. Контроллер возвращает Mono (см. GatewayRegistrationController.register)
        // 2. Spring получает HTTP запрос и вызывает контроллер
        // 3. Контроллер вызывает registrationService.registerUser(request)
        // 4. Spring автоматически делает subscribe() на возвращаемом Mono
        // 5. ТОЛЬКО ТОГДА выполняется запрос к auth-service
        Mono<TokenResponse> credentials = createCredentialsInAuthService(request);
        
        // ========== ПРОВЕРКА: Есть ли данные профиля? ==========
        // Если данных профиля НЕТ - идем по короткому пути (только credentials)
        if (request.getFirstName() == null || request.getFirstName().isBlank() ||
            request.getLastName() == null || request.getLastName().isBlank() ||
            request.getBirthDate() == null) {
            
            log.info("No profile data provided. Registration completed with credentials only.");
            
            // ========== ПЕРВЫЙ RETURN (строка ~80): Возвращаем только credentials ==========
            // ВАЖНО: этот return означает ВЫХОД ИЗ МЕТОДА registerUser
            // Код после этого if НЕ ВЫПОЛНИТСЯ, если условие true
            // Преобразуем Mono<TokenResponse> в Mono<RegisterResponse>
            // Возвращаем Mono<RegisterResponse> - это и есть результат метода registerUser
            return credentials.map(token -> {
                // ВАЖНО: этот return НЕ выходит из метода registerUser!
                // Это return внутри lambda функции (map), который возвращает RegisterResponse
                // Этот RegisterResponse будет обернут в Mono и вернется из метода registerUser
                RegisterResponse response = new RegisterResponse();
                response.setMessage("Credentials registered successfully. Please login to get tokens and create profile.");
                response.setUser(null);
                response.setTokens(null);
                return response; // ← Возвращает RegisterResponse в map, а не из метода registerUser
            })
            .onErrorResume(error -> {
                log.error("Error creating credentials for user {}: {}", request.getLogin(), error.getMessage());
                return Mono.error(new RuntimeException("Registration error: " + error.getMessage(), error));
            });
            // КОНЕЦ МЕТОДА для случая "только credentials"
        }
        
        // ========== ЕСЛИ ДАННЫЕ ПРОФИЛЯ ЕСТЬ - ПРОДОЛЖАЕМ ==========
        // Этот код выполнится ТОЛЬКО если условие if было false (есть данные профиля)
        
        // Шаг 2: Логинимся для получения токена
        // flatMap: после успешного создания credentials выполняем логин
        // credentials содержит TokenResponse от регистрации, но нам нужен новый токен от логина
        Mono<TokenResponse> loginTokens = credentials.flatMap(token -> loginUser(request.getLogin(), request.getPassword()))
                // Обработка ошибки логина: если логин не удался - откатываем credentials
                .onErrorResume(loginError -> {
                    log.error("Error during login for user {}. Rolling back credentials...", request.getLogin(), loginError);
                    return rollbackAuthServiceCredentials(request.getLogin())
                            .then(Mono.error(new RuntimeException(
                                    "Login failed. Credentials rolled back. " + loginError.getMessage(), loginError)));
                });
        
        // Шаг 3: Создаем профиль пользователя
        // flatMap необходим потому что createUserProfileWithToken возвращает Mono<UserDto>
        // Внутри flatMap используем map для преобразования UserDto в RegisterResponse
        Mono<RegisterResponse> registrationResponse = loginTokens.flatMap(tokens -> {
            String accessToken = tokens.getAccessToken();
            // createUserProfileWithToken возвращает Mono<UserDto>, поэтому нужен flatMap
            return createUserProfileWithToken(request, accessToken)
                    // map преобразует UserDto в RegisterResponse
                    .map(userDto -> {
                        RegisterResponse response = new RegisterResponse();
                        response.setMessage("User registered successfully with profile. Tokens included.");
                        response.setUser(userDto);
                        response.setTokens(tokens);
                        return response;
                    })
                    // Обработка ошибки создания профиля: если профиль не создался - откатываем credentials
                    .onErrorResume(profileError -> {
                        log.error("Error creating user profile for {}. Rolling back credentials...", 
                                request.getLogin(), profileError);
                        return rollbackAuthServiceCredentials(request.getLogin())
                                .then(Mono.error(new RuntimeException(
                                        "Profile creation failed. Credentials rolled back. " + 
                                        profileError.getMessage(), profileError)));
                    });
        });
        
        // ========== ВТОРОЙ RETURN (строка ~135): Для случая "credentials + профиль" ==========
        // Этот return выполнится ТОЛЬКО если условие if было false (есть данные профиля)
        // Возвращаем результат полной регистрации (credentials + логин + профиль)
        // Оба return (строка 79 и строка ~135) возвращают Mono<RegisterResponse>
        return registrationResponse;
    }



    /**
     * Создает credentials в Authentication Service.
     */
    private Mono<TokenResponse> createCredentialsInAuthService(RegisterRequest req) {
        // Подготавливаем данные для auth-service (только login, password, role)
        // Используем Map.of для создания неизменяемой карты (Java 9+)
        Map<String, Object> authRequest = Map.of(
            "login", req.getLogin(),
            "password", req.getPassword(),
            "role", req.getRole() != null ? req.getRole() : "ROLE_USER"
        );

        String url = authServiceBase + "/auth/v1/register";
        log.debug("Creating credentials in Authentication Service: {}", url);

        // Выполняем POST запрос к auth-service
        WebClient.RequestHeadersSpec<?> request = webClient.post()
                .uri(url)
                .bodyValue(authRequest);

        // forward - вспомогательный метод, который выполняет запрос через WebClient
        // и преобразует ответ в TokenResponse
        return forward(request, TokenResponse.class);
    }

    /**
     * Выполняет логин пользователя для получения токена.
     */
    private Mono<TokenResponse> loginUser(String login, String password) {
        Map<String, Object> loginRequest = Map.of(
            "login", login,
            "password", password
        );

        String url = authServiceBase + "/auth/v1/login";
        log.debug("Logging in user: {}", url);

        WebClient.RequestHeadersSpec<?> request = webClient.post()
                .uri(url)
                .bodyValue(loginRequest);

        return forward(request, TokenResponse.class);
    }

    /**
     * Создает профиль пользователя в User Service используя токен.
     */
    private Mono<UserDto> createUserProfileWithToken(RegisterRequest request, String token) {
        String email = jwtTokenService.extractEmailFromToken(token);
        if (email == null) {
            return Mono.error(new RuntimeException("Could not extract email from token"));
        }

        CreateUserFromTokenRequest profileRequest = new CreateUserFromTokenRequest();
        profileRequest.setFirstName(request.getFirstName());
        profileRequest.setLastName(request.getLastName());
        profileRequest.setBirthDate(request.getBirthDate());

        String url = userServiceBase + "/api/v1/users/createUser";
        log.debug("Creating user profile in User Service: {}", url);

        // Выполняем POST запрос к user-service с токеном в заголовке
        WebClient.RequestHeadersSpec<?> webRequest = webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + token)
                .bodyValue(profileRequest);

        return forward(webRequest, UserDto.class);
    }

    /**
     * Откатывает credentials из Authentication Service при ошибке.
     * Выполняет DELETE запрос к auth-service для удаления пользователя по email.
     */
    private Mono<Void> rollbackAuthServiceCredentials(String loginOrEmail) {
        log.warn("Rolling back credentials for user: {}", loginOrEmail);

        // DELETE запрос к auth-service для удаления пользователя
        String url = authServiceBase + "/auth/v1/internal/sync/users/" + loginOrEmail;
        WebClient.RequestHeadersSpec<?> deleteRequest = webClient.delete().uri(url);

        // internalApiKey используется для внутренних запросов между сервисами
        // Если настроен - добавляем в заголовок для аутентификации
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            deleteRequest = deleteRequest.header("X-Internal-Api-Key", internalApiKey);
        }

        // Выполняем DELETE запрос напрямую (forwardVoid инлайнен, так как используется только здесь)
        return deleteRequest.retrieve()
                .toBodilessEntity()
                .onErrorResume(WebClientResponseException.class,
                        ex -> Mono.error(new DownstreamServiceException(ex)))
                .then()
                .doOnSuccess(v -> log.info("Credentials successfully rolled back for user: {}", loginOrEmail))
                .onErrorResume(error -> {
                    log.error("Error rolling back credentials for user: {}", loginOrEmail, error);
                    return Mono.empty();
                });
    }

    // ========== Старые методы для обратной совместимости (пока пусть будут) ==========

    /**
     * @deprecated Пока не удаляем registerUser() для полной регистрации с Saga паттерном.
     * Регистрирует только credentials в Authentication Service.
     */
    @Deprecated
    public Mono<RegisterResponse> registerCredentials(RegisterRequest request) {
        log.info("Registering credentials for user: {}", request.getLogin());

        return createCredentialsInAuthService(request)
                .map(tokenResponse -> {
                    log.info("Credentials registered successfully for user: {}. User should login to get tokens.",
                            request.getLogin());
                    RegisterResponse response = new RegisterResponse();
                    response.setMessage("Credentials registered successfully. Please login to get tokens and create profile.");
                    response.setUser(null);
                    response.setTokens(null);
                    return response;
                });
    }

    /**
     * @deprecated Используйте registerUser() для полной регистрации с Saga паттерном.
     * Создает профиль пользователя в User Service с поддержкой rollback.
     */
    @Deprecated
    public Mono<UserDto> createUserProfile(CreateUserFromTokenRequest request, String authHeader) {
        String token = jwtTokenService.extractTokenFromHeader(authHeader);
        if (token == null) {
            return Mono.error(new RuntimeException("Invalid Authorization header"));
        }

        String email = jwtTokenService.extractEmailFromToken(token);
        if (email == null) {
            return Mono.error(new RuntimeException("Could not extract email from token"));
        }

        log.info("Creating user profile for email: {}", email);

        Mono<UserDto> result = createUserProfileInUserService(email, request, token)
                .doOnSuccess(userDto -> log.info("User profile successfully created: {}", userDto.getEmail()));

        result = result.onErrorResume(error -> {
            log.error("Error creating profile for user {}. Performing rollback...", email, error);
            String errorMessage = "Profile creation error. Credentials rolled back. " + error.getMessage();
            return rollbackAuthServiceCredentials(email)
                    .then(Mono.error(new RuntimeException(errorMessage, error)));
        });

        return result;
    }

    /**
     * @deprecated Используется только в старом методе createUserProfile().
     */
    @Deprecated
    private Mono<UserDto> createUserProfileInUserService(
            String email,
            CreateUserFromTokenRequest request,
            String token) {

        log.debug("Calling User Service to create user profile: {}", userServiceBase + "/api/v1/users/createUser");

        WebClient.RequestHeadersSpec<?> requestSpec = webClient.post()
                .uri(userServiceBase + "/api/v1/users/createUser")
                .header("Authorization", "Bearer " + token)
                .bodyValue(request);

        return forward(requestSpec, UserDto.class)
                .doOnError(error -> log.error("Error calling User Service: {}", error.getMessage()));
    }

    /**
     * Вспомогательный метод для выполнения HTTP запроса через WebClient.
     * Выполняет запрос и преобразует ответ в указанный тип.
     * 
     * @param req подготовленный запрос (POST, GET, PUT, DELETE и т.д.)
     * @param bodyClass класс для преобразования ответа (UserDto.class, TokenResponse.class и т.д.)
     * @return Mono с результатом запроса
     */
    private <T> Mono<T> forward(WebClient.RequestHeadersSpec<?> req, Class<T> bodyClass) {
        return req.retrieve()
                .bodyToMono(bodyClass)
                .onErrorResume(WebClientResponseException.class,
                        ex -> Mono.error(new DownstreamServiceException(ex)));
    }

}

