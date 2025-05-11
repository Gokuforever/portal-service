package com.sorted.portal.bl_services;

import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.GoogleDriveService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ManageFileUpload_BLService {

    private final GoogleDriveService googleDriveService;

    public ManageFileUpload_BLService(GoogleDriveService googleDriveService) {
        this.googleDriveService = googleDriveService;
    }

    @GetMapping("/download")
    public SEResponse fetchPhoto(@RequestParam("fileId") String fileId) throws Exception {
        String fetchPhoto = googleDriveService.fetchPhoto(fileId);
        return SEResponse.getBasicSuccessResponseObject(fetchPhoto, ResponseCode.SUCCESSFUL);
    }
}
