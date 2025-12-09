package com.example.demo.dto;

import lombok.Data;

/**
 * DTO для ответа от authentication-service с JWT токенами
 */
@Data
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private String type;
    private Long expiresIn;
}

