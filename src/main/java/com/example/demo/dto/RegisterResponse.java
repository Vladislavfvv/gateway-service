package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для ответа при регистрации пользователя (Saga паттерн).
 * Может содержать данные пользователя, если профиль был создан автоматически.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {
    private String message;
    private UserDto user; // Может быть null, если профиль не создавался
    private TokenResponse tokens; // Может быть null, если профиль не создавался
    
    public static RegisterResponse credentialsOnly(String login) {
        return new RegisterResponse(
            "Credentials registered successfully. Please login to get tokens and create profile.",
            null,
            null
        );
    }
    
    public static RegisterResponse withProfile(UserDto user, TokenResponse tokens) {
        return new RegisterResponse(
            "User registered successfully with profile. Tokens included.",
            user,
            tokens
        );
    }
}

