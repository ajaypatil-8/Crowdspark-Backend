package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.service.CloudinaryService;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

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

            Map<String, String> result = new HashMap<>();
            result.put("secure_url", uploadResult.get("secure_url").toString());
            result.put("public_id", uploadResult.get("public_id").toString());

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Cloudinary upload failed: " + e.getMessage());
        }
    }


    @Override
    public void deleteFile(String publicId) {
        try {
            cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.asMap("resource_type", "image")
            );
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Cloudinary delete failed: " + e.getMessage());
        }
    }
}