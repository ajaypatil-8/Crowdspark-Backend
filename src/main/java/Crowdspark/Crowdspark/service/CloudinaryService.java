package Crowdspark.Crowdspark.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

public interface CloudinaryService {


    String uploadFile(MultipartFile file, String folder);


    Map<String, String> uploadFileWithDetails(MultipartFile file, String folder);


    void deleteFile(String publicId);
}