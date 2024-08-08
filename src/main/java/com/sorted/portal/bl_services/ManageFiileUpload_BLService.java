package com.sorted.portal.bl_services;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.GoogleDriveService;

@RestController
public class ManageFiileUpload_BLService {

	private final GoogleDriveService googleDriveService;

	public ManageFiileUpload_BLService(GoogleDriveService googleDriveService) {
		this.googleDriveService = googleDriveService;
	}

	@PostMapping("/upload")
	public SEResponse uploadPhoto(@RequestParam("file") MultipartFile file) {
		try {
			String fileId = googleDriveService.uploadPhoto(file);
			return SEResponse.getBasicSuccessResponseObject(fileId, null);
		} catch (IOException | GeneralSecurityException e) {
			throw new CustomIllegalArgumentsException("Failed to upload file: " + e.getMessage());
		}
	}

	@GetMapping("/download")
	public SEResponse fetchPhoto(@RequestParam("fileId") String fileId,
			@RequestParam("destinationPath") String destinationPath) {
		try {
			googleDriveService.fetchPhoto(fileId, destinationPath);
			return SEResponse.getBasicSuccessResponseObject(fileId, null);
		} catch (IOException | GeneralSecurityException e) {
			throw new CustomIllegalArgumentsException("Failed to download file: " + e.getMessage());
		}
	}
}
