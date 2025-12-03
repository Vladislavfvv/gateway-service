package com.example.demo.controller;

import com.example.demo.dto.CreateUserFromTokenRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.dto.RegisterResponse;
import com.example.demo.dto.UserDto;
import com.example.demo.service.GatewayRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Контроллер для кастомного flow регистрации пользователя.
 * Обрабатывает HTTP запросы и делегирует бизнес-логику в GatewayRegistrationService.
 * 
 * Flow регистрации:
 * 1. Регистрация credentials в Authentication Service (POST /auth/v1/register) - только login, password, role
 * 2. Логин для получения токенов (POST /auth/v1/login) - проксируется к authentication-service
 * 3. Создание профиля пользователя в User Service (POST /auth/v1/createUser) - firstName, lastName, birthDate
 * С поддержкой rollback при ошибках на этапе создания профиля.
 */
@Slf4j
@RestController
@RequestMapping("/auth/v1")
@RequiredArgsConstructor
public class GatewayRegistrationController {
    
    private final GatewayRegistrationService registrationService;

    /**
     * Шаг 1: Регистрация credentials в Authentication Service.
     * Принимает только login, password, role.
     * Создает credentials, но НЕ выдает токены.
     * Токены выдаются при логине (POST /auth/v1/login).
     * 
     * @param request данные для регистрации (login, password, role)
     * @return ResponseEntity с сообщением об успешной регистрации
     */
    @PostMapping("/register")
    public Mono<ResponseEntity<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.registerCredentials(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
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
    public Mono<ResponseEntity<UserDto>> createUser(
            @Valid @RequestBody CreateUserFromTokenRequest request,
            @RequestHeader("Authorization") String authHeader) {
        
        return registrationService.createUserProfile(request, authHeader)
                .map(userDto -> ResponseEntity.status(HttpStatus.CREATED).body(userDto));
    }
}
