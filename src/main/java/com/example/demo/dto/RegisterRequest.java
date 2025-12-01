package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO для регистрации пользователя в Authentication Service.
 * Содержит только credentials (login, password, role).
 * Профиль пользователя создаётся отдельно через /auth/createUser.
 */
@Data
public class RegisterRequest {
    @NotBlank(message = "Login is required")
    private String login;

    @NotBlank(message = "Password is required")
    private String password;

    private String role = "ROLE_USER";
}
