package com.sorted.portal.bl_services;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sorted.commons.beans.AddressDTO;
import com.sorted.commons.beans.Bank_Details;
import com.sorted.commons.beans.OTPResponse;
import com.sorted.commons.beans.Spoc_Details;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.Address;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Plans;
import com.sorted.commons.entity.mongo.Role;
import com.sorted.commons.entity.mongo.Seller;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.Address_Service;
import com.sorted.commons.entity.service.Plans_Service;
import com.sorted.commons.entity.service.RoleService;
import com.sorted.commons.entity.service.Seller_Service;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.All_Status.Seller_Status;
import com.sorted.commons.enums.EntityDetails;
import com.sorted.commons.enums.Permission;
import com.sorted.commons.enums.ProcessType;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.enums.UserType;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.OrderBy;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.SortOrder;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.Pagination;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.manage.otp.ManageOtp;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.PasswordValidatorUtils;
import com.sorted.commons.utils.SERegExpUtils;
import com.sorted.commons.utils.ValidationUtil;
import com.sorted.portal.request.beans.CUDSellerBean;
import com.sorted.portal.request.beans.FidnSellerBean;
import com.sorted.portal.request.beans.VerifyOtpBean;
import com.sorted.portal.response.beans.FindResBean;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/seller")
public class ManageSeller_BLService {

	@Autowired
	private Users_Service users_Service;

	@Autowired
	private Seller_Service seller_Service;

	@Autowired
	private ManageOtp manageOtp;

	@Autowired
	private Address_Service address_Service;

	@Autowired
	private Plans_Service plans_Service;

	@Autowired
	private RoleService roleService;

	private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@PostMapping("/create")
	public SEResponse create(@RequestBody SERequest request, HttpServletRequest servletRequest) {
		try {
			log.info("/seller/create:: API started!");
			CUDSellerBean req = request.getGenericRequestDataObject(CUDSellerBean.class);
			CommonUtils.extractHeaders(servletRequest, req);

			UsersBean usersBean = users_Service.validateUserForActivity(req, Permission.EDIT,
					Activity.SELLER_ONBOARDING);
			Role role = usersBean.getRole();
			if (!UserType.SUPER_ADMIN.equals(role.getUser_type())) {
				throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
			}
			if (!StringUtils.hasText(req.getName())) {
				throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SELLER_NAME);
			}
			if (!SERegExpUtils.standardTextValidation(req.getName())) {
				throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SELLER_NAME);
			}
			if (!StringUtils.hasText(req.getPan_no())) {
				throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_PAN_NO);
			}
			if (!SERegExpUtils.isPan(req.getPan_no())) {
				throw new CustomIllegalArgumentsException(ResponseCode.INVALID_PAN_NO);
			}
			AddressDTO addressDTO = req.getAddress();
			if (addressDTO == null) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_ADDRESS);
			}
			Address address = ValidationUtil.validateAddress(addressDTO);
			List<Spoc_Details> spoc_details = req.getSpoc_details();
			if (CollectionUtils.isEmpty(spoc_details)) {
				throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SPOC);
			}
			List<String> unique_phone = new ArrayList<>();
			Spoc_Details primary_spoc = null;
			for (Spoc_Details spoc : spoc_details) {
				if (!StringUtils.hasText(spoc.getFirst_name())) {
					throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SPOC_FNAME);
				}
				if (!SERegExpUtils.standardTextValidation(spoc.getFirst_name())) {
					throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SPOC_FNAME);
				}
				if (!StringUtils.hasText(spoc.getLast_name())) {
					throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SPOC_LNAME);
				}
				if (!SERegExpUtils.standardTextValidation(spoc.getLast_name())) {
					throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SPOC_LNAME);
				}
				if (!StringUtils.hasText(spoc.getEmail_id())) {
					throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SPOC_MAIL);
				}
				if (!SERegExpUtils.isEmail(spoc.getEmail_id())) {
					throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SPOC_MAIL);
				}
				if (!StringUtils.hasText(spoc.getMobile_no())) {
					throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SPOC_PHONE);
				}
				if (!SERegExpUtils.isMobileNo(spoc.getMobile_no())) {
					throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SPOC_PHONE);
				}
				if (!StringUtils.hasText(spoc.getDesignation())) {
					throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SPOC_DESIGNATION);
				}
				if (!SERegExpUtils.isMobileNo(spoc.getDesignation())) {
					throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SPOC_DESIGNATION);
				}
				if (unique_phone.contains(spoc.getMobile_no())) {
					throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SPOC_DESIGNATION);
				}
				if (spoc.is_primary() && primary_spoc == null) {
					primary_spoc = spoc;
				} else {
					spoc.set_primary(false);
				}
			}
			if (primary_spoc == null) {
				throw new CustomIllegalArgumentsException(ResponseCode.MARK_PRIMARY);
			}
			Bank_Details bank_details = req.getBank_details();
			if (bank_details == null) {
				throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_BANK_DETAILS);
			}
			ValidationUtil.validateBankDetails(bank_details);
			List<String> serviceable_pincodes = req.getServiceable_pincodes();
			if (CollectionUtils.isEmpty(serviceable_pincodes)) {
				throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SERVICEABLE_PINCODE);
			}
			for (String pincode : serviceable_pincodes) {
				if (!SERegExpUtils.isPincode(pincode)) {
					throw new CustomIllegalArgumentsException(ResponseCode.INVALID_PINCODE);
				}
			}

			SEFilter filterS = new SEFilter(SEFilterType.AND);
			filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
			filterS.addClause(WhereClause.in(Spoc_Details.Fields.mobile_no, unique_phone));

			Seller seller = seller_Service.repoFindOne(filterS);
			if (seller != null && !Seller_Status.IN_PROGRESS.equals(seller.getStatus())) {
				throw new CustomIllegalArgumentsException(ResponseCode.DUPLICATE_SE_MOBILE);
			}
			if (seller == null) {
				seller = new Seller();
			}

			SEFilter filterU = new SEFilter(SEFilterType.AND);
			filterU.addClause(WhereClause.eq(Users.Fields.mobile_no, primary_spoc.getMobile_no()));
			filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			long duplicate = users_Service.countByFilter(filterU);
			if (duplicate > 0) {
				throw new CustomIllegalArgumentsException(ResponseCode.DUPLICATE_USER_MOBILE);
			}
			filterU = new SEFilter(SEFilterType.AND);
			filterU.addClause(WhereClause.eq(Users.Fields.email_id, primary_spoc.getEmail_id()));
			filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			duplicate = users_Service.countByFilter(filterU);
			if (duplicate > 0) {
				throw new CustomIllegalArgumentsException(ResponseCode.DUPLICATE_USER_EMAIL);
			}
			String name = CommonUtils.toTitleCase(req.getName());
			String pan_no = req.getPan_no();
			seller.setBusiness_name(name);
			seller.setSpoc_details(spoc_details);
			seller.setServiceable_pincodes(serviceable_pincodes);
			seller.setCompany_pan(pan_no);
			seller.setBank_details(bank_details);

			seller = seller_Service.upsert(seller.getId(), seller, usersBean.getId());
			String uuid = manageOtp.send(primary_spoc.getMobile_no(), seller.getId(), ProcessType.SELLER_ONBOARDING,
					EntityDetails.SELLER, usersBean.getId());

			address.setUser_type(UserType.SELLER);
			address.setEntity_id(seller.getId());
			address_Service.create(address, req.getReq_user_id());

			OTPResponse response = new OTPResponse();
			response.setReference_id(uuid);
			response.setEntity_id(seller.getId());
			response.setProcess_type(ProcessType.SELLER_ONBOARDING.name());
			log.info("/seller/create:: API ended");
			return SEResponse.getBasicSuccessResponseObject(response, ResponseCode.SUCCESSFUL);
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("/seller/create:: exception occurred");
			log.error("/seller/create:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}

	@PostMapping("/verify")
	public void verify(@RequestBody SERequest request, HttpServletRequest servletRequest) {
		try {
			log.info("/seller/create:: API started!");
			VerifyOtpBean req = request.getGenericRequestDataObject(VerifyOtpBean.class);
			CommonUtils.extractHeaders(servletRequest, req);

			UsersBean usersBean = users_Service.validateUserForActivity(req, Permission.EDIT,
					Activity.SELLER_ONBOARDING);
			Role role = usersBean.getRole();
			if (!UserType.SUPER_ADMIN.equals(role.getUser_type())) {
				throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
			}
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
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_REF_ID);
			}
			if (!StringUtils.hasText(entity_id)) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_ENTITY);
			}

			manageOtp.verify(EntityDetails.SELLER, reference_id, otp, entity_id, ProcessType.SELLER_ONBOARDING,
					usersBean.getId());

			SEFilter filterS = new SEFilter(SEFilterType.AND);
			filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, entity_id));
			filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			Seller seller = seller_Service.repoFindOne(filterS);
			if (seller == null) {
				throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
			}
			if (!Seller_Status.IN_PROGRESS.equals(seller.getStatus())) {
				throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SELLER_STATUS);
			}
			Optional<Spoc_Details> findFirst = seller.getSpoc_details().stream().filter(e -> e.is_primary())
					.findFirst();
			if (findFirst.isEmpty()) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PRIMARY_SPOC);
			}

			Spoc_Details spoc_Details = findFirst.get();

			SEFilter filterU = new SEFilter(SEFilterType.AND);
			filterU.addClause(WhereClause.eq(Users.Fields.mobile_no, spoc_Details.getMobile_no()));
			filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			long duplicate = users_Service.countByFilter(filterU);
			if (duplicate > 0) {
				throw new CustomIllegalArgumentsException(ResponseCode.DUPLICATE_USER_MOBILE);
			}
			filterU = new SEFilter(SEFilterType.AND);
			filterU.addClause(WhereClause.eq(Users.Fields.email_id, spoc_Details.getEmail_id()));
			filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			duplicate = users_Service.countByFilter(filterU);
			if (duplicate > 0) {
				throw new CustomIllegalArgumentsException(ResponseCode.DUPLICATE_USER_EMAIL);
			}

			SEFilter filterP = new SEFilter(SEFilterType.AND);
			filterP.addClause(WhereClause.eq(Plans.Fields.name, Defaults.DEFAULT_SELLER_PLAN));
			filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			Plans plan = plans_Service.repoFindOne(filterP);
			if (plan == null) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_DEF_SELLER_PLAN);
			}

			Users user = new Users();
			user.setFirst_name(spoc_Details.getFirst_name());
			user.setLast_name(spoc_Details.getLast_name());
			user.setMobile_no(spoc_Details.getMobile_no());
			user.setEmail_id(spoc_Details.getEmail_id());
			String generatePassword = PasswordValidatorUtils.generatePassword();
			String encode = passwordEncoder.encode(generatePassword);
			user.setPassword(encode);

			List<Role> roles = plan.getRoles();
			roles.stream().forEach(e -> {
				e.setSeller_code(seller.getCode());
				e.setSeller_id(seller.getId());
				e.setUser_type(UserType.SELLER);
				e.setUser_type_id(UserType.SELLER.getId());
				Role temp = roleService.create(e, usersBean.getId());
				if (e.getName().equals("ADMIN")) {
					user.setRole_id(temp.getId());
				}
			});

			users_Service.create(user, usersBean.getId());

			// TODO: send email notification

		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("/seller/create:: exception occurred");
			log.error("/seller/create:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}

	@PostMapping("/find")
	public SEResponse find(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
		try {
			log.info("find seller API started.");
			FidnSellerBean req = request.getGenericRequestDataObject(FidnSellerBean.class);
			CommonUtils.extractHeaders(httpServletRequest, req);
			UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(),
					Activity.SELLER_MANAGEMENT);

			Role role = usersBean.getRole();
			switch (role.getUser_type()) {
			case SUPER_ADMIN:
				break;
			default:
				throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
			}

			FindResBean bean = new FindResBean();

			SEFilter filterS = new SEFilter(SEFilterType.AND);
			if (StringUtils.hasText(req.getName())) {
				filterS.addClause(WhereClause.like(Seller.Fields.business_name, req.getName().trim()));
			}

			filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			OrderBy orderBy = new OrderBy();
			orderBy.setKey(BaseMongoEntity.Fields.modification_date);
			orderBy.setType(SortOrder.DESC);
			filterS.setOrderBy(orderBy);

			long total_count = seller_Service.countByFilter(filterS);
			if (total_count == 0) {
				return SEResponse.getBasicSuccessResponseObject(bean, ResponseCode.NO_RECORD);
			}

			int page = req.getPage();
			int size = req.getSize();
			Pagination pagination = new Pagination(page, size);

			filterS.setPagination(pagination);

			List<Seller> listS = seller_Service.repoFind(filterS);
			if (CollectionUtils.isEmpty(listS)) {
				return SEResponse.getBasicSuccessResponseObject(bean, ResponseCode.NO_RECORD);
			}

			if (pagination.getPage() == 0) {
				bean.setTotal_count(total_count);
			}

			bean.setList(listS);
			return SEResponse.getBasicSuccessResponseObject(bean, ResponseCode.NO_RECORD);

		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("/seller/create:: exception occurred");
			log.error("/seller/create:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}
}
