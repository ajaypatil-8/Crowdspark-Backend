package Crowdspark.Crowdspark.dto;

import Crowdspark.Crowdspark.entity.type.MediaType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class ProjectMediaResponse {
    private Long id;
    private Long projectId;
    private MediaType type;
    private String url;
    private LocalDateTime createdAt;
}

