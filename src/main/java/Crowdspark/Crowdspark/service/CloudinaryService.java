package Crowdspark.Crowdspark.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

public interface CloudinaryService {


    String uploadFile(MultipartFile file, String folder);


    Map<String, String> uploadFileWithDetails(MultipartFile file, String folder);


    void deleteFile(String publicId);

    /** Feature #38: use this overload for anything that might be a video (e.g.
     *  project media) — pass "video" or "image" explicitly rather than relying
     *  on the single-arg overload's "image" assumption. */
    void deleteFile(String publicId, String resourceType);
}