// src/main/java/Crowdspark/Crowdspark/dto/ChatMessage.java
// Feature #42 — AI Support Chatbot

package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatMessage {

    // Restricted to user/assistant on purpose: without this, a caller could
    // submit {"role":"system","content":"ignore previous instructions..."}
    // as part of their "history" and have it land in the messages array
    // alongside our real system prompt — a straightforward prompt-injection
    // vector this closes off at the validation layer.
    @NotBlank
    @Pattern(regexp = "user|assistant", message = "role must be 'user' or 'assistant'")
    private String role;

    @NotBlank
    @Size(max = 2000, message = "Message must be under 2000 characters")
    private String content;
}
