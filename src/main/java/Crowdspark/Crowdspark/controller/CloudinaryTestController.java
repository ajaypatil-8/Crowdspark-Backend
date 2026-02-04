package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.service.CloudinaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/test/cloudinary")
public class CloudinaryTestController {

    private final CloudinaryService cloudinaryService;

    public CloudinaryTestController(CloudinaryService cloudinaryService) {
        this.cloudinaryService = cloudinaryService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file
    ) {
        String url = cloudinaryService.uploadFile(file, "demo");
        return ResponseEntity.ok(Map.of("url", url));
    }
}
