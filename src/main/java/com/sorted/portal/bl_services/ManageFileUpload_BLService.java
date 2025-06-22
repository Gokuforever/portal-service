package com.sorted.portal.bl_services;

import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.CloudinaryService;
import com.sorted.commons.utils.GoogleDriveService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ManageFileUpload_BLService {

    private final GoogleDriveService googleDriveService;
    private final CloudinaryService cloudinaryService;

    public ManageFileUpload_BLService(GoogleDriveService googleDriveService, CloudinaryService cloudinaryService) {
        this.googleDriveService = googleDriveService;
        this.cloudinaryService = cloudinaryService;
    }

    @GetMapping("/download")
    @Deprecated
    public SEResponse fetchPhoto(@RequestParam("fileId") String fileId) throws Exception {
        String fetchPhoto = googleDriveService.fetchPhoto(fileId);
        return SEResponse.getBasicSuccessResponseObject(fetchPhoto, ResponseCode.SUCCESSFUL);
    }

    @PostMapping("/upload/cloudinary/full")
    @Deprecated
    public SEResponse uploadToCloudinaryFull(@RequestParam("file") MultipartFile file) {
        try {
            var result = cloudinaryService.uploadImage(file);
            return SEResponse.getBasicSuccessResponseObject(result, ResponseCode.SUCCESSFUL);
        } catch (Exception e) {
            return SEResponse.getBadRequestFailureResponse(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/upload/cloudinary/url")
    public String uploadToCloudinaryUrl(@RequestParam("file") MultipartFile file) {
        try {
            return cloudinaryService.uploadImageAndGetUrl(file);
        } catch (Exception e) {
            return null;
        }
    }
}
