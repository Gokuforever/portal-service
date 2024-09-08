package com.sorted.portal.bl_services;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.GoogleDriveService;

@RestController
public class ManageFileUpload_BLService {

	private final GoogleDriveService googleDriveService;

	public ManageFileUpload_BLService(GoogleDriveService googleDriveService) {
		this.googleDriveService = googleDriveService;
	}

//	@Autowired
//	private Users_Service users_Service;

//	@PostMapping("/uploadfile")
//	public SEResponse uploadPhoto(@RequestParam("file") MultipartFile file, HttpServletRequest httpServletRequest) {
//		try {
//			String req_user_id = httpServletRequest.getHeader("req_user_id").toString();
//			String req_role_id = httpServletRequest.getHeader("req_role_id").toString();
//			if (!StringUtils.hasText(req_user_id) || !StringUtils.hasText(req_role_id)) {
//				throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
//			}
//			UsersBean usersBean = users_Service.validateUserForActivity(req_user_id, Activity.INVENTORY_MANAGEMENT);
//			String fileId = googleDriveService.uploadPhoto(file, usersBean, DocumentType.PRODUCT_IMAGE);
//			return SEResponse.getBasicSuccessResponseObject(fileId, ResponseCode.SUCCESSFUL);
//		} catch (IOException | GeneralSecurityException e) {
//			e.printStackTrace();
//			return SEResponse.getBadRequestFailureResponse(ResponseCode.ERR_0001);
//		}
//	}

	@GetMapping("/download")
	public SEResponse fetchPhoto(@RequestParam("fileId") String fileId,
			@RequestParam("destinationPath") String destinationPath) {
		googleDriveService.fetchPhoto(fileId, destinationPath);
		return SEResponse.getBasicSuccessResponseObject(fileId, null);

	}
}
