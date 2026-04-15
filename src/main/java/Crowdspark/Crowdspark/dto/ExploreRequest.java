package Crowdspark.Crowdspark.dto;

import lombok.Data;

@Data
public class ExploreRequest {

    // filter by category (optional)
    private Long categoryId;

    // keyword search on title/shortDescription (optional)
    private String keyword;

    // sort: NEWEST | TRENDING | MOST_FUNDED
    private String sort = "NEWEST";

    // pagination
    private int page = 0;
    private int size = 12;
}
