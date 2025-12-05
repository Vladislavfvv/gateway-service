package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для ответа при регистрации credentials.
 * Возвращает только сообщение об успехе, без токенов.
 * Токены выдаются при логине.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {
    private String message;
    
    public static RegisterResponse success(String login) {
        return new RegisterResponse("User registered successfully. Please login to get tokens.");
    }
}

