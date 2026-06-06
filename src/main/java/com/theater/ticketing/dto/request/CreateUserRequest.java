package com.theater.ticketing.dto.request;

import com.theater.ticketing.model.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    @NotBlank(message = "Username must not be blank")
    private String username;

    @NotBlank
    @Email(message = "Must be a valid email")
    private String email;

    @NotBlank(message = "Password must not be blank")
    private String password;

    @NotNull(message = "Role must not be null")
    private UserRole role;
}
