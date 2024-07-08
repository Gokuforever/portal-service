package com.sorted.portal.signup;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Otp;
import com.sorted.commons.entity.mongo.Role;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.Otp_Service;
import com.sorted.commons.entity.service.RoleService;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.ProcessType;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.enums.UserType;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.manage.otp.ManageOtp;
import com.sorted.commons.utils.PasswordValidatorUtils;
import com.sorted.commons.utils.SERegExpUtils;
import com.sorted.portal.request.beans.SignUpRequest;
import com.sorted.portal.request.beans.VerifySignUp;
import com.sorted.portal.response.beans.OTPResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class ManageSignUp_BLService {

	@Autowired
	private Otp_Service otp_Service;

	@Autowired
	private ManageOtp manageOtp;

	@Autowired
	private Users_Service users_Service;

	@Autowired
	private RoleService roleService;

	private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@Value("${se.portal.customer.signup.role}")
	private String customer_signup_role;
	
//	private String getShortIdentified() {
//		return this.getClass().getSimpleName();
//	}

	@PostMapping("/signup")
	public SEResponse signUp(@RequestBody SERequest request, HttpServletRequest servletRequest) {
		try {
			log.info("signUp:: API started");
			SignUpRequest req = request.getGenericRequestDataObject(SignUpRequest.class);
			if (!StringUtils.hasText(req.getFirst_name())) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_FN);
			}
			if (!StringUtils.hasText(req.getLast_name())) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_LN);
			}
			if (!StringUtils.hasText(req.getMobile_no())) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_MN);
			}
			if (!StringUtils.hasText(req.getEmail_id())) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_EI);
			}
			if (!StringUtils.hasText(req.getPassword())) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PASS);
			}
			String password = req.getPassword().trim();
			PasswordValidatorUtils.validatePassword(password);
			String encode = passwordEncoder.encode(password);

			SEFilter filterM = new SEFilter(SEFilterType.AND);
			filterM.addClause(WhereClause.eq(Users.Fields.mobile_no, req.getMobile_no()));
			filterM.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			Users user = users_Service.repoFindOne(filterM);
			if (user == null) {
				user = new Users();
			} else if (user.getIs_verified()) {
				throw new CustomIllegalArgumentsException(ResponseCode.ALREADY_SIGNED_UP);
			}

			SEFilter filterE = new SEFilter(SEFilterType.AND);
			filterE.addClause(WhereClause.eq(Users.Fields.email_id, req.getEmail_id()));
			filterE.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
			if (user != null) {
				filterE.addClause(WhereClause.notEq(BaseMongoEntity.Fields.id, user.getId()));
			}

			long emailCount = users_Service.countByFilter(filterE);
			if (emailCount > 0) {
				throw new CustomIllegalArgumentsException(ResponseCode.DUPLICATE_EMAIL);
			}

			user.setFirst_name(req.getFirst_name());
			user.setLast_name(req.getLast_name());
			user.setMobile_no(req.getMobile_no());
			user.setEmail_id(req.getEmail_id());
			user.setPassword(encode);
			user.setUser_type(UserType.CUSTOMER);

			user = users_Service.upsert(user.getId(), user, Defaults.SIGN_UP);

			SEFilter filterO = new SEFilter(SEFilterType.AND);
			filterO.addClause(WhereClause.eq(Otp.Fields.mobile_no, req.getMobile_no()));
			filterO.addClause(WhereClause.eq(Otp.Fields.status, true));
			filterO.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			List<Otp> listOtp = otp_Service.repoFind(filterO);
			if (!CollectionUtils.isEmpty(listOtp)) {
				for (Otp otp : listOtp) {
					otp.setStatus(false);
					otp_Service.update(otp.getId(), otp, Defaults.SIGN_UP);
				}
			}
			String uuid = manageOtp.send(req.getMobile_no(), user.getId(), ProcessType.SIGN_UP);
			OTPResponse response = new OTPResponse();
			response.setEntity_id(user.getId());
			response.setReference_id(uuid);
			log.info("signUp:: API ended");
			return SEResponse.getBasicSuccessResponseObject(response, ResponseCode.SUCCESSFUL);
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("signUp:: exception occurred");
			log.error("signUp:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}

	@PostMapping("/signup/verify")
	public SEResponse verify(@RequestBody SERequest request) {
		try {
			log.info("/signup/verify:: API started");
			VerifySignUp req = request.getGenericRequestDataObject(VerifySignUp.class);
			String otp = req.getOtp();
			String entity_id = req.getEntity_id();
			String reference_id = req.getReference_id();
			if (!StringUtils.hasText(otp)) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_OTP);
			}
			if (!SERegExpUtils.isOtp(otp)) {
				throw new CustomIllegalArgumentsException(ResponseCode.INVALID_OTP);
			}
			if (!StringUtils.hasText(entity_id)) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_ENTITY);
			}
			if (!StringUtils.hasText(entity_id)) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_ENTITY);
			}
			if (!StringUtils.hasText(reference_id)) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_OTP_REF);
			}

			SEFilter filterCL = new SEFilter(SEFilterType.AND);
			filterCL.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, entity_id));
			filterCL.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			Users users = users_Service.repoFindOne(filterCL);
			if (users == null) {
				throw new CustomIllegalArgumentsException(ResponseCode.ENTITY_NOT_FOUND);
			}

			SEFilter filterR = new SEFilter(SEFilterType.AND);
			filterR.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, customer_signup_role));
			filterR.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			Role role = roleService.repoFindOne(filterR);
			if (role == null) {
				throw new CustomIllegalArgumentsException(ResponseCode.MSSING_CUST_DEF_ROLE);
			}

			manageOtp.verify(users.getMobile_no(), entity_id, reference_id, otp, ProcessType.SIGN_UP, Defaults.SIGN_UP);
			users.setIs_verified(true);
			users.setRole_id(customer_signup_role);
			users_Service.create(users, Defaults.SIGN_UP);

			UsersBean usersBean = users_Service.validateUserForLogin(users.getId(), users.getRole_id());

			return SEResponse.getBasicSuccessResponseObject(usersBean, ResponseCode.SUCCESSFUL);
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("/signup/verify:: exception occurred");
			log.error("/signup/verify:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}
}
