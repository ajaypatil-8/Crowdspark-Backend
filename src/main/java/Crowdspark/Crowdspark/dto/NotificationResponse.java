package Crowdspark.Crowdspark.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse implements Serializable {
    private Long          id;
    private String        type;
    private String        title;
    private String        message;
    private String        link;
    private Long          referenceId;
    private boolean       read;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
