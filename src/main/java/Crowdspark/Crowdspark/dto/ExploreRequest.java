// src/main/java/Crowdspark/Crowdspark/dto/ExploreRequest.java
// Feature #25 — Input Validation Hardening
// Changes:
//   • keyword:   added @Size(max=255) — unbounded before; a giant keyword causes
//                a full PostgreSQL tsvector search + DB I/O
//   • sort:      added @Pattern — was raw String; an invalid value would fall
//                through to the service and cause an IllegalArgumentException
//   • minGoal / maxGoal: added @PositiveOrZero + @Max guards
//   • page:      added @Min(0)
//   • size:      added @Min(1) @Max(50) — prevents "size=99999" full-table dump

package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ExploreRequest {

    /** Filter by category id (optional) */
    @Positive(message = "Category ID must be a positive number")
    private Long categoryId;

    /** Full-text search keyword (optional) — uses PostgreSQL tsvector when set */
    @Size(max = 255, message = "Keyword must be 255 characters or less")
    private String keyword;

    /** Sort order — must be one of the accepted enum values */
    @Pattern(
        regexp  = "NEWEST|TRENDING|MOST_FUNDED|ENDING_SOON",
        message = "Sort must be one of: NEWEST, TRENDING, MOST_FUNDED, ENDING_SOON"
    )
    private String sort = "NEWEST";

    /** Minimum goal amount filter in ₹ */
    @PositiveOrZero(message = "Minimum goal must be zero or positive")
    @Max(value = 100_000_000, message = "Minimum goal cannot exceed ₹10 crore")
    private Double minGoal;

    /** Maximum goal amount filter in ₹ */
    @PositiveOrZero(message = "Maximum goal must be zero or positive")
    @Max(value = 100_000_000, message = "Maximum goal cannot exceed ₹10 crore")
    private Double maxGoal;

    /** Zero-based page index */
    @Min(value = 0, message = "Page must be 0 or greater")
    private int page = 0;

    /** Number of results per page — capped at 50 to prevent full-table dumps */
    @Min(value = 1,  message = "Page size must be at least 1")
    @Max(value = 50, message = "Page size cannot exceed 50")
    private int size = 12;
}
