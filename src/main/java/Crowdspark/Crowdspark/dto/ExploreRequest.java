package Crowdspark.Crowdspark.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExploreRequest {
    private Long categoryId;
    @Builder.Default private String sort = "newest";
    @Builder.Default private int page = 0;
    @Builder.Default private int size = 12;
}
