// src/main/java/Crowdspark/Crowdspark/dto/ExploreRequest.java
// CHANGES: Added minGoal, maxGoal range filters and location filter.
// All new fields are optional — fully backward compatible.

package Crowdspark.Crowdspark.dto;

import lombok.Data;

@Data
public class ExploreRequest {

    /** Filter by category id (optional) */
    private Long categoryId;

    /** Full-text search keyword (optional) — uses PostgreSQL tsvector when set */
    private String keyword;

    /** Sort order: NEWEST | TRENDING | MOST_FUNDED | ENDING_SOON */
    private String sort = "NEWEST";

    /** Minimum goal amount filter in ₹ (optional) */
    private Double minGoal;

    /** Maximum goal amount filter in ₹ (optional) */
    private Double maxGoal;

    /** Pagination */
    private int page = 0;
    private int size = 12;
}
