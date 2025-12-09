package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDate;

/**
 * DTO для полной регистрации пользователя (Saga паттерн).
 * Содержит credentials (login, password, role) и опциональные данные профиля.
 * Если указаны firstName, lastName, birthDate - профиль создается автоматически.
 * Если не указаны - создаются только credentials, профиль можно создать позже.
 */
@Data
public class RegisterRequest {
    @NotBlank(message = "Login is required")
    private String login;

    @NotBlank(message = "Password is required")
    private String password;

    private String role = "ROLE_USER";

    // Опциональные поля для автоматического создания профиля
    private String firstName;
    private String lastName;

    @PastOrPresent(message = "Birth date cannot be in the future")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate birthDate;

    /**
     * Проверяет, указаны ли все необходимые поля для создания профиля.
     */
    public boolean hasProfileData() {
        return firstName != null && !firstName.isBlank() &&
               lastName != null && !lastName.isBlank() &&
               birthDate != null;
    }
}
