package Crowdspark.Crowdspark.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;



import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectListResponse {

    private Long id;
    private String title;
    private String shortDescription;
    private String thumbnailUrl;

    private String creatorUsername;

    private Double goalAmount;
    private Double currentAmount;

    private LocalDateTime createdAt;


}
