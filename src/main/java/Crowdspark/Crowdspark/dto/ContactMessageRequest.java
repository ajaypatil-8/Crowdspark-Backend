package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContactMessageRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 120, message = "Name must be 120 characters or less")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 180, message = "Email must be 180 characters or less")
    private String email;

    @NotBlank(message = "Topic is required")
    @Size(max = 80, message = "Topic must be 80 characters or less")
    private String topic;

    @NotBlank(message = "Message is required")
    @Size(max = 1000, message = "Message must be 1000 characters or less")
    private String message;
}