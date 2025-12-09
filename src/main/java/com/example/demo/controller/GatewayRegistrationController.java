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
import org.springframework.http.MediaType;
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

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        boolean hasProfileData = request.getFirstName() != null && !request.getFirstName().isBlank() &&
                request.getLastName() != null && !request.getLastName().isBlank() &&
                request.getBirthDate() != null;
        log.info("Received registration request for user: {} (profile data: {})", 
                request.getLogin(), hasProfileData ? "provided" : "not provided");
        
        return registrationService.registerUser(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .onErrorResume(error -> {
                    log.error("Registration failed for user: {}", request.getLogin(), error);
                    
                    // Определяем HTTP статус на основе типа ошибки
                    HttpStatus status;
                    String errorMessage = error.getMessage() != null ? error.getMessage() : "Unknown error";
                    
                    if (errorMessage.contains("Missing required profile data")) {
                        // Ошибка валидации - отсутствуют обязательные поля
                        status = HttpStatus.BAD_REQUEST;
                    } else if (errorMessage.contains("Registration error") || 
                               errorMessage.contains("Credentials rolled back")) {
                        // Ошибка при регистрации с rollback
                        status = HttpStatus.INTERNAL_SERVER_ERROR;
                    } else {
                        // Другие ошибки
                        status = HttpStatus.INTERNAL_SERVER_ERROR;
                    }
                    
                    // Возвращаем понятное сообщение об ошибке клиенту
                    RegisterResponse errorResponse = new RegisterResponse();
                    errorResponse.setMessage(errorMessage);
                    errorResponse.setUser(null);
                    errorResponse.setTokens(null);
                    
                    return Mono.just(ResponseEntity.status(status).body(errorResponse));
                });
    }
}
