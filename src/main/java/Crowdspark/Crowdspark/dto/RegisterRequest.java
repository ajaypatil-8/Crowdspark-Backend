package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 30)
    private String username;

    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;


    @Pattern(regexp = "^(\\+91)?[6-9]\\d{9}$", message = "Enter a valid Indian mobile number")
    private String phoneNumber;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}