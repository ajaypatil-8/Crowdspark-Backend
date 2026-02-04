package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.service.CloudinaryService;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
public class CloudinaryServiceImpl implements CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryServiceImpl(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @Override
    public String uploadFile(MultipartFile file, String folder) {
        try {
            // 🔍 Detect file type
            String contentType = file.getContentType();
            String resourceType = "image";

            if (contentType != null && contentType.startsWith("video")) {
                resourceType = "video";
            }

            // ☁️ Upload to Cloudinary
            Map uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", resourceType
                    )
            );

            // 🔗 Return secure CDN URL
            return uploadResult.get("secure_url").toString();

        } catch (Exception e) {
            e.printStackTrace(); // keep for debugging
            throw new RuntimeException("Cloudinary upload failed: " + e.getMessage());
        }
    }
}
