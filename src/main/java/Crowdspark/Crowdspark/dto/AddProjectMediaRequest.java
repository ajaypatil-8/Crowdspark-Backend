package Crowdspark.Crowdspark.dto;

import Crowdspark.Crowdspark.entity.type.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data

public class AddProjectMediaRequest {
    @NotNull(message = "Media type is required")
    private MediaType type;

    @NotBlank(message = "Media url is required")
    @Size(max = 2000)
    private String url;
}
