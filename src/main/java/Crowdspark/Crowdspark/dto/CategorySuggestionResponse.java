// src/main/java/Crowdspark/Crowdspark/dto/CategorySuggestionResponse.java
// Feature #47 — AI Auto-Tagging & Category Detection

package Crowdspark.Crowdspark.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategorySuggestionResponse {

    /** 0-2 ids, always validated against the platform's real category list
     *  before being returned — never an id the model invented. */
    private List<Long> categoryIds;

    private String reasoning;
}
