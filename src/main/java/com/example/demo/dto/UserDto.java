package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

/*  DTO для создания пользователя в user-service */
@Data
public class UserDto {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate birthDate;
    
    private List<CardInfoDto> cards;
}
