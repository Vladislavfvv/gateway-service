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


    public Mono<RegisterResponse> registerUser(RegisterRequest request) {
        log.info("Starting user registration: {}", request.getLogin());
        
        // ========== ПРОВЕРКА: Все данные профиля должны быть указаны ==========
        // если нет полных данных профиля, регистрация отклоняется

        java.util.List<String> missingFields = new java.util.ArrayList<>();
        
        if (request.getFirstName() == null || request.getFirstName().isBlank()) {
            missingFields.add("firstName");
        }
        if (request.getLastName() == null || request.getLastName().isBlank()) {
            missingFields.add("lastName");
        }
        if (request.getBirthDate() == null) {
            missingFields.add("birthDate");
        }
        
        if (!missingFields.isEmpty()) {
            String missingFieldsList = String.join(", ", missingFields);
            String errorMessage = String.format(
                    "Registration failed: Missing required profile data fields: %s. " +
                    "Saga pattern requires complete data (firstName, lastName, birthDate) for both credentials and profile creation.",
                    missingFieldsList);
            
            log.error("Registration rejected for user {}: missing profile data fields - {}", 
                    request.getLogin(), missingFieldsList);
            
            // Mono.error() создает Mono с ошибкой, которая будет обработана Spring WebFlux
            //ошибка будет передана клиенту через HTTP ответ
            return Mono.error(new RuntimeException(errorMessage));
        }
        
        // ========== ШАГ 1: Создаем credentials в Authentication Service (сохранение в auth_db) ==========

        Mono<TokenResponse> credentials = createCredentialsInAuthService(request);
        
        // ========== ШАГ 2: Логинимся для получения токена ==========
        
        Mono<TokenResponse> loginTokens = credentials.flatMap(token -> loginUser(request.getLogin(), request.getPassword()))
                // Обработка ошибки логина: если логин не удался - откатываем credentials
                .onErrorResume(loginError -> {
                    log.error("Error during login for user {}. Rolling back credentials...", request.getLogin(), loginError);
                    return rollbackAuthServiceCredentials(request.getLogin())
                            .then(Mono.error(new RuntimeException(
                                    "Login failed. Credentials rolled back. " + loginError.getMessage(), loginError)));
                });
        
        // ========== ШАГ 3: Создаем профиль пользователя в User Service (сохранение в us_db) ==========
        
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
        //  выполнится ТОЛЬКО если условие if было false (есть данные профиля)
        // Возвращаем результат полной регистрации (credentials + логин + профиль)       
        return registrationResponse;
    }



    /**
     * Создает credentials в Authentication Service.
     */
    private Mono<TokenResponse> createCredentialsInAuthService(RegisterRequest req) {
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

        // Выполняем DELETE запрос к auth-service для удаления credentials
        // Это реактивная цепочка операций, которая выполнится только при подписке (subscribe)
        return deleteRequest
                // Шаг 1: retrieve() - выполняет HTTP запрос и получает ответ                
                .retrieve()                
                // Шаг 2: toBodilessEntity() - преобразует ответ в ResponseEntity без тела
                // DELETE запрос обычно не возвращает тело, только статус код
                // Возвращает Mono<ResponseEntity<Void>>
                .toBodilessEntity()
                
                // Шаг 3: onErrorResume() - обрабатывает ошибки HTTP запроса
                // Если auth-service вернул ошибку (404, 500 и т.д.) - ловим WebClientResponseException
                // Преобразуем в DownstreamServiceException для единообразной обработки ошибок
                .onErrorResume(WebClientResponseException.class,
                        ex -> Mono.error(new DownstreamServiceException(ex)))
                
                // Шаг 4: then() - игнорируем результат (ResponseEntity) и возвращаем Mono<Void>
                // Нам не нужен ответ от auth-service, только факт выполнения запроса
                // Преобразует Mono<ResponseEntity<Void>> в Mono<Void>
                .then()
                
                // Шаг 5: doOnSuccess() - выполняется при успешном удалении credentials
                // Это side-effect операция (логирование), не изменяет результат
                // v - это Void (пустое значение), так как мы в Mono<Void>
                .doOnSuccess(v -> log.info("Credentials successfully rolled back for user: {}", loginOrEmail))
                
                // Шаг 6: onErrorResume() - финальная обработка любых ошибок
                // Если что-то пошло не так (даже после предыдущей обработки) - логируем и продолжаем
                // Возвращаем Mono.empty() вместо ошибки, чтобы не прервать цепочку
                // Это важно: даже если rollback не удался, мы не хотим прерывать обработку основной ошибки
                .onErrorResume(error -> {
                    log.error("Error rolling back credentials for user: {}", loginOrEmail, error);
                    return Mono.empty(); // Возвращаем пустой Mono вместо ошибки
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
     * Вспомогательный метод для выполнения HTTP запроса через WebClient.
     * Выполняет запрос и преобразует ответ в указанный тип.
     * 
     *  req подготовленный запрос (POST, GET, PUT, DELETE и т.д.)
     *  bodyClass класс для преобразования ответа (UserDto.class, TokenResponse.class и т.д.)
     * return Mono с результатом запроса
     */
    private <T> Mono<T> forward(WebClient.RequestHeadersSpec<?> req, Class<T> bodyClass) {
        return req.retrieve() //retrieve - это метод для выполнения запроса
                .bodyToMono(bodyClass)
                .onErrorResume(WebClientResponseException.class,
                        ex -> Mono.error(new DownstreamServiceException(ex)));
    }

}

