// src/main/java/Crowdspark/Crowdspark/dto/SupportChatResponse.java
// Feature #42 — AI Support Chatbot

package Crowdspark.Crowdspark.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportChatResponse {

    private String reply;

    /** true when the bot doesn't know, the question needs account-specific
     *  lookup it has no access to, or the user asked for a human. Frontend
     *  shows a "Contact support" CTA when this is true. */
    private boolean escalate;
}
