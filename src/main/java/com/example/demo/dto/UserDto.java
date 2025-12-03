package com.example.demo.dto;

import lombok.Data;
/*  DTO для создания пользователя в user-service */
@Data
public class UserDto {
    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
}
