package com.sorted.portal.bl_services;

import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.GoogleDriveService;
import com.sorted.commons.utils.CloudinaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
public class ManageFileUpload_BLService {

    private final GoogleDriveService googleDriveService;
    private final CloudinaryService cloudinaryService;

    public ManageFileUpload_BLService(GoogleDriveService googleDriveService, CloudinaryService cloudinaryService) {
        this.googleDriveService = googleDriveService;
        this.cloudinaryService = cloudinaryService;
    }

    @GetMapping("/download")
    public SEResponse fetchPhoto(@RequestParam("fileId") String fileId) throws Exception {
        String fetchPhoto = googleDriveService.fetchPhoto(fileId);
        return SEResponse.getBasicSuccessResponseObject(fetchPhoto, ResponseCode.SUCCESSFUL);
    }

    @PostMapping("/upload/cloudinary/full")
    public SEResponse uploadToCloudinaryFull(@RequestParam("file") MultipartFile file) {
        try {
            var result = cloudinaryService.uploadImage(file);
            return SEResponse.getBasicSuccessResponseObject(result, ResponseCode.SUCCESSFUL);
        } catch (Exception e) {
            return SEResponse.getBadRequestFailureResponse(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/upload/cloudinary/url")
    public SEResponse uploadToCloudinaryUrl(@RequestParam("file") MultipartFile file) {
        try {
            String url = cloudinaryService.uploadImageAndGetUrl(file);
            Map<String, String> result = new HashMap<>();
            result.put("url", url);
            return SEResponse.getBasicSuccessResponseObject(result, ResponseCode.SUCCESSFUL);
        } catch (Exception e) {
            return SEResponse.getBadRequestFailureResponse(ResponseCode.ERR_0001);
        }
    }
}
