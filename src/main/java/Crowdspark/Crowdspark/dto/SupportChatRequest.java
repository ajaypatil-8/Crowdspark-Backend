// src/main/java/Crowdspark/Crowdspark/dto/SupportChatRequest.java
// Feature #42 — AI Support Chatbot

package Crowdspark.Crowdspark.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SupportChatRequest {

    // The whole conversation so far, ending with the newest user message.
    // Frontend keeps the full local history; only the last 12 are actually
    // sent to Groq (see AiServiceImpl) to keep prompt size and latency
    // bounded, but validation allows up to 30 so a long session doesn't
    // start hard-failing before that trim kicks in.
    @NotEmpty(message = "Conversation is empty")
    @Size(max = 30, message = "Conversation too long — please start a new chat")
    @Valid
    private List<ChatMessage> messages;
}
