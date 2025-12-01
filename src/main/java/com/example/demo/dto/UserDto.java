package com.example.demo.dto;

import lombok.Data;
/* UserDto - класс, который используется для представления пользователя */
@Data
public class UserDto {
    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
}
