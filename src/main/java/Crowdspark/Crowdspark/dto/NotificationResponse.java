package Crowdspark.Crowdspark.dto;

import java.io.Serializable;
import lombok.Builder;
import java.io.Serializable;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse implements Serializable {
    private Long id;
    private String type;
    private String title;
    private String message;
    private String link;
    private Long referenceId;
    private boolean read;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
