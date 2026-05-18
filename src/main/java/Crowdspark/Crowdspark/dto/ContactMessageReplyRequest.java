package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContactMessageReplyRequest {

    @NotBlank(message = "Subject is required")
    @Size(max = 180, message = "Subject must be 180 characters or less")
    private String subject;

    @NotBlank(message = "Reply message is required")
    @Size(max = 5000, message = "Reply message must be 5000 characters or less")
    private String message;
}