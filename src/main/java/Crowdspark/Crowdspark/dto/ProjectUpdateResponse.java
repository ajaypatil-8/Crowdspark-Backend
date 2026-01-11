package Crowdspark.Crowdspark.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectUpdateResponse {
    private Long id;
    private String title;
    private String content;
    private LocalDateTime createdAt;
}
