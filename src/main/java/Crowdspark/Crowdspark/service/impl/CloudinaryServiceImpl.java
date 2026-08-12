package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.service.CloudinaryService;
import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class CloudinaryServiceImpl implements CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryServiceImpl(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }


    @Override
    public String uploadFile(MultipartFile file, String folder) {
        return uploadFileWithDetails(file, folder).get("secure_url");
    }

    @Override
    public Map<String, String> uploadFileWithDetails(MultipartFile file, String folder) {
        try {
            String contentType = file.getContentType();
            String resourceType = "image";

            if (contentType != null && contentType.startsWith("video")) {
                resourceType = "video";
            }

            Map uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", resourceType
                    )
            );

            String publicId = uploadResult.get("public_id").toString();

            String optimizedUrl = cloudinary.url()
                    .resourceType(resourceType)
                    .secure(true)
                    .transformation(new Transformation().fetchFormat("auto").quality("auto"))
                    .generate(publicId);

            Map<String, String> result = new HashMap<>();
            result.put("secure_url", optimizedUrl);
            result.put("public_id", publicId);

            return result;

        } catch (Exception e) {
            log.error("Cloudinary upload failed for folder '{}': {}", folder, e.getMessage(), e);
            throw new RuntimeException("Cloudinary upload failed: " + e.getMessage());
        }
    }


    @Override
    public void deleteFile(String publicId) {
        deleteFile(publicId, "image");
    }

    @Override
    public void deleteFile(String publicId, String resourceType) {

        try {
            cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.asMap("resource_type", resourceType == null ? "image" : resourceType)
            );
        } catch (Exception e) {
            log.error("Cloudinary delete failed for publicId '{}' (resourceType={}): {}",
                    publicId, resourceType, e.getMessage(), e);
            throw new RuntimeException("Cloudinary delete failed: " + e.getMessage());
        }
    }
}