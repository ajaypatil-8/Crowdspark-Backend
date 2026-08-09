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

            // Feature #38: f_auto/q_auto lets Cloudinary serve the smallest format a
            // requesting browser actually supports (WebP/AVIF instead of PNG/JPEG
            // where possible) at the smallest acceptable quality, automatically —
            // this is what "reduce origin bandwidth" concretely means for media
            // already hosted on Cloudinary. Cloudinary's res.cloudinary.com URLs are
            // ALREADY served through Cloudinary's own global CDN — stacking a SECOND
            // CDN (CloudFront/Cloudflare) in front of an already-CDN-backed host
            // would add a redundant hop and extra cost, not reduce bandwidth
            // further. The uploadResult's raw secure_url (no transformation applied)
            // is intentionally NOT what gets returned/persisted below.
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
        // BUG FIX: resource_type used to be hardcoded to "image" regardless of what
        // was actually deleted. Nothing currently calls this for video content (all
        // 3 existing call sites — profile image, banner image, KYC documents — are
        // genuinely always images), so this hasn't caused a visible failure yet, but
        // Cloudinary requires the CORRECT resource_type to locate an asset for
        // deletion — calling destroy() with the wrong one fails to find it, silently
        // leaving it in storage. This overload exists so any future caller deleting
        // project media (which CAN be video — see ProjectMedia.mediaType) gets this
        // right from the start, without needing to touch this file again.
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