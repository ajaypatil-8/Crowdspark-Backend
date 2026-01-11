package Crowdspark.Crowdspark.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentResponse {

    private Long id;
    private Long userId;
    private String content;
    private LocalDateTime createdAt;
    private List<CommentResponse> replies;
}
