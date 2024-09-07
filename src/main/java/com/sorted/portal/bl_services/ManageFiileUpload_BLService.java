package com.sorted.portal.bl_services;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.GoogleDriveService;

@RestController
public class ManageFiileUpload_BLService {

	private final GoogleDriveService googleDriveService;

	public ManageFiileUpload_BLService(GoogleDriveService googleDriveService) {
		this.googleDriveService = googleDriveService;
	}

	@PostMapping("/upload")
	public SEResponse uploadPhoto() {
		String fileId = null;
		try {
			fileId = googleDriveService.uploadPhoto("E:\\kittu all photos\\22 Feb kittu\\97324e.jpg");
		} catch (IOException | GeneralSecurityException e) {
			e.printStackTrace();
		}
		return SEResponse.getBasicSuccessResponseObject(fileId, ResponseCode.SUCCESSFUL);
	}

	@GetMapping("/download")
	public SEResponse fetchPhoto(@RequestParam("fileId") String fileId,
			@RequestParam("destinationPath") String destinationPath) {
		googleDriveService.fetchPhoto(fileId, destinationPath);
		return SEResponse.getBasicSuccessResponseObject(fileId, null);

	}
}
