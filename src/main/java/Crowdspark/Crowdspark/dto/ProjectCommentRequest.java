// src/main/java/Crowdspark/Crowdspark/dto/ProjectCommentRequest.java
package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectCommentRequest {

    @NotBlank(message = "Comment cannot be empty")
    @Size(max = 2000, message = "Comment must be under 2000 characters")
    private String content;

    /** If set, this is a reply to an existing comment */
    private Long parentCommentId;
}
