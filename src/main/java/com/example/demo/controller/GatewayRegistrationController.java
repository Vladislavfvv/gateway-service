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
 * Контроллер для регистрации пользователя с использованием Saga паттерна.
 * Gateway выступает оркестратором распределенной транзакции.
 * 
 * Saga Flow регистрации (один эндпоинт /register):
 * 1. Создание credentials в Authentication Service
 * 2. Логин для получения токена (если указаны данные профиля)
 * 3. Создание профиля в User Service (если указаны firstName, lastName, birthDate)
 * 
 * При ошибках выполняется компенсирующая транзакция (rollback).
 */
@Slf4j
@RestController
@RequestMapping("/auth/v1")
@RequiredArgsConstructor
public class GatewayRegistrationController {
    
    private final GatewayRegistrationService registrationService;

    /**
     * Единый эндпоинт для регистрации пользователя (Saga паттерн).
     * 
     * Если указаны только login, password, role - создаются только credentials.
     * Если дополнительно указаны firstName, lastName, birthDate - выполняется полный цикл:
     * создание credentials → логин → создание профиля.
     * 
     * При ошибках на любом этапе выполняется rollback (удаление credentials).
     * 
     * @param request данные для регистрации:
     *                - обязательные: login, password
     *                - опциональные: role (по умолчанию ROLE_USER)
     *                - опциональные для автоматического создания профиля: firstName, lastName, birthDate
     * @return ResponseEntity с результатом регистрации:
     *         - если профиль не создавался: только сообщение
     *         - если профиль создавался: данные пользователя и токены
     */
    @PostMapping("/register")
    public Mono<ResponseEntity<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        boolean hasProfileData = request.getFirstName() != null && !request.getFirstName().isBlank() &&
                request.getLastName() != null && !request.getLastName().isBlank() &&
                request.getBirthDate() != null;
        log.info("Received registration request for user: {} (profile data: {})", 
                request.getLogin(), hasProfileData ? "provided" : "not provided");
        
        return registrationService.registerUser(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .doOnError(error -> log.error("Registration failed for user: {}", request.getLogin(), error));
    }

    /**
     * @deprecated Используйте /register с полными данными для автоматического создания профиля.
     * Создание профиля пользователя в User Service (для обратной совместимости).
     * Требует предварительной регистрации credentials и логина для получения токена.
     */
    @Deprecated
    @PostMapping("/createUser")
    public Mono<ResponseEntity<UserDto>> createUser(
            @Valid @RequestBody CreateUserFromTokenRequest request,
            @RequestHeader("Authorization") String authHeader) {
        
        return registrationService.createUserProfile(request, authHeader)
                .map(userDto -> ResponseEntity.status(HttpStatus.CREATED).body(userDto));
    }
}
