package Crowdspark.Crowdspark.dto;

import Crowdspark.Crowdspark.entity.type.ContactMessageStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ContactMessageResponse {
    private Long id;
    private String name;
    private String email;
    private String topic;
    private String message;
    private ContactMessageStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
    private LocalDateTime repliedAt;
    private String replySubject;
    private String replyMessage;
    private String repliedByName;
}