package com.sorted.portal.bl_services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sorted.commons.beans.OTPResponse;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.EntityDetails;
import com.sorted.commons.enums.ProcessType;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.manage.otp.ManageOtp;
import com.sorted.commons.utils.SERegExpUtils;
import com.sorted.portal.request.beans.LoginBean;
import com.sorted.portal.request.beans.ResendOtpBean;
import com.sorted.portal.request.beans.VerifyOtpBean;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequestMapping("/auth")
@RestController
public class ManageLogin_BLService {

	@Autowired
	private Users_Service users_Service;

	@Autowired
	private ManageOtp manageOtp;

	@PostMapping("/signin")
	public SEResponse signin(@RequestBody SERequest request) {
		try {
			LoginBean req = request.getGenericRequestDataObject(LoginBean.class);
			if (!StringUtils.hasText(req.getMobile_no())) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_MOBILE);
			}
			if (!StringUtils.hasText(req.getPassword())) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PASS);
			}
			OTPResponse response = users_Service.validateUserForLogin(req.getMobile_no(), req.getPassword());
			log.info("signin:: API ended");
			return SEResponse.getBasicSuccessResponseObject(response, ResponseCode.SUCCESSFUL);
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("signin:: exception occurred");
			log.error("signin:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}

	@PostMapping("/verifyOtp")
	public SEResponse verifyOtp(@RequestBody SERequest request) {
		try {
			log.info("auth/verifyOtp:: API started!");
			VerifyOtpBean req = request.getGenericRequestDataObject(VerifyOtpBean.class);
			String otp = req.getOtp();
			String reference_id = req.getReference_id();
			String entity_id = req.getEntity_id();
			if (!StringUtils.hasText(otp)) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_OTP);
			}
			if (!SERegExpUtils.isOtp(otp)) {
				throw new CustomIllegalArgumentsException(ResponseCode.INVALID_OTP);
			}
			if (!StringUtils.hasText(reference_id)) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_OTP_REF);
			}
			if (!StringUtils.hasText(entity_id)) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_ENTITY);
			}
			manageOtp.verify(EntityDetails.USERS, reference_id, otp, entity_id, ProcessType.SIGN_IN, Defaults.SIGN_IN);
			UsersBean usersBean = users_Service.validateAndGetUserInfo(entity_id);
			return SEResponse.getBasicSuccessResponseObject(usersBean, ResponseCode.SUCCESSFUL);
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("auth/verifyOtp:: exception occurred");
			log.error("auth/verifyOtp:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}

	@PostMapping("/resendOtp")
	public SEResponse resendOtp(@RequestBody SERequest request) {
		try {
			log.info("auth/resendOtp:: API started!");
			ResendOtpBean req = request.getGenericRequestDataObject(ResendOtpBean.class);
			ProcessType process_type = req.getProcess_type();
			String reference_id = req.getReference_id();
			String entity_id = req.getEntity_id();
			if (process_type == null) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PROCESS_TYPE);
			}
			if (!StringUtils.hasText(reference_id)) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_OTP_REF);
			}
			if (!StringUtils.hasText(entity_id)) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_ENTITY);
			}
			String uuid = manageOtp.resendOtp(process_type, reference_id, entity_id);
			OTPResponse response = new OTPResponse();
			response.setReference_id(uuid);
			response.setEntity_id(entity_id);
			response.setProcess_type(process_type.name());
			log.info("auth/resendOtp:: API ended!");
			return SEResponse.getBasicSuccessResponseObject(response, ResponseCode.SUCCESSFUL);
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("auth/resendOtp:: exception occurred");
			log.error("auth/resendOtp:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}

}
