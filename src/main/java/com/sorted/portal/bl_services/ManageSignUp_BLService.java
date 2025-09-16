package com.sorted.portal.bl_services;

import com.sorted.commons.beans.EducationCategoryBean;
import com.sorted.commons.beans.OTPResponse;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Role;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.RoleService;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.MailTemplate;
import com.sorted.commons.enums.ProcessType;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.MailBuilder;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.manage.otp.ManageOTPManagerService;
import com.sorted.commons.manage.otp.ManageOtp;
import com.sorted.commons.notifications.EmailSenderImpl;
import com.sorted.commons.utils.PasswordValidatorUtils;
import com.sorted.commons.utils.Preconditions;
import com.sorted.commons.utils.SERegExpUtils;
import com.sorted.portal.annotation.RateLimited;
import com.sorted.portal.request.beans.AuthV2Bean;
import com.sorted.portal.request.beans.SignUpRequest;
import com.sorted.portal.request.beans.VerifyOtpBean;
import com.sorted.portal.response.beans.AuthV2Response;
import com.sorted.portal.response.beans.SendOtpReq;
import com.sorted.portal.service.AuthService;
import com.sorted.portal.service.EducationDetailsValidationService;
import com.sorted.portal.service.SignUpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static com.sorted.portal.service.CookieService.setCookies;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ManageSignUp_BLService {

    private final ManageOtp manageOtp;
    private final ManageOTPManagerService otpManagerService;
    private final AuthService authService;
    private final Users_Service users_Service;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SignUpService signUpService;
    private final EmailSenderImpl emailSenderImpl;
    private final EducationDetailsValidationService validationService;
    @Value("${se.portal.customer.signup.role}")
    private String customer_signup_role;

    @RateLimited(50)
    @PostMapping("/otp")
    public String sendOpt(@RequestBody SendOtpReq request) {
        String mobileNo = request.mobileNo();
        Preconditions.check(StringUtils.hasText(mobileNo), ResponseCode.MISSING_MN);
        Preconditions.check(SERegExpUtils.isMobileNo(mobileNo), ResponseCode.INVALID_MN);
        return otpManagerService.send(mobileNo, ProcessType.AUTH, Defaults.AUTH);
    }

    @PostMapping("v2/auth")
    public AuthV2Response signUpV2(@RequestBody AuthV2Bean auth, HttpServletRequest httpServletRequest) {
        Users user = authService.verifyAuth(auth, ProcessType.AUTH, httpServletRequest);

        UsersBean usersBean = users_Service.validateAndGetUserInfo(user.getId());
        return AuthV2Response.builder()
                .usersBean(usersBean)
                .redirectionUrl(auth.redirectToCart() ? "/order/bag" : "/")
                .build();
    }

//    @PostMapping("/signup/verifyOtp/v2")
//    public UsersBean verifyOtpV2(@RequestBody VerifyOtpBean verifyOtpBean, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
//        String otp = verifyOtpBean.getOtp();
//        String reference_id = verifyOtpBean.getReference_id();
//        String entity_id = verifyOtpBean.getEntity_id();
//
//        Preconditions.check(StringUtils.hasText(otp), ResponseCode.MISSING_OTP);
//        Preconditions.check(SERegExpUtils.isOtp(otp), ResponseCode.INVALID_OTP);
//        Preconditions.check(StringUtils.hasText(reference_id), ResponseCode.MISSING_REF_ID);
//        Preconditions.check(StringUtils.hasText(entity_id), ResponseCode.MISSING_ENTITY);
//
//        manageOtp.verify(reference_id, otp, ProcessType.SIGN_UP, Defaults.SIGN_UP);
//
//        SEFilter filterCL = new SEFilter(SEFilterType.AND);
//        filterCL.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, entity_id));
//        filterCL.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
//
//        Users users = users_Service.repoFindOne(filterCL);
//        if (users == null) {
//            throw new CustomIllegalArgumentsException(ResponseCode.ENTITY_NOT_FOUND);
//        }
//
//        SEFilter filterR = new SEFilter(SEFilterType.AND);
//        filterR.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, customer_signup_role));
//        filterR.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
//
//        Role role = roleService.repoFindOne(filterR);
//        if (role == null) {
//            throw new CustomIllegalArgumentsException(ResponseCode.MSSING_CUST_DEF_ROLE);
//        }
//        users.setIs_verified(true);
//        users.setRole_id(customer_signup_role);
//
//        String guest_user_id = httpServletRequest.getHeader("req_user_id");
//        if (StringUtils.hasText(guest_user_id)) {
//            SEFilter filterGU = new SEFilter(SEFilterType.AND);
//            filterGU.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, guest_user_id));
//            filterGU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
//            Users guestUser = users_Service.repoFindOne(filterGU);
//            if (guestUser != null) {
//                EducationCategoryBean educationDetails = guestUser.getEducationDetails();
//                if (educationDetails != null) {
//                    users.setEducationDetails(educationDetails);
//                }
//                users_Service.deleteOne(guest_user_id, users.getId());
//                signUpService.migrateCart(guest_user_id, users.getId());
//                signUpService.migrateAddressForCustomer(guest_user_id, users.getId());
//            }
//        }
//        users_Service.update(users.getId(), users, Defaults.SIGN_UP);
//
//        UsersBean usersBean = users_Service.validateAndGetUserInfo(users.getId());
//        setCookies(httpServletRequest, httpServletResponse, usersBean);
//        return usersBean;
//    }


    @PostMapping("/signup")
    public SEResponse signUp(@RequestBody SERequest request) {
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
            if (!SERegExpUtils.isAlphabeticString(req.getFirst_name())) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_FN);
            }
            if (!SERegExpUtils.isAlphabeticString(req.getLast_name())) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_LN);
            }
            if (!SERegExpUtils.isMobileNo(req.getMobile_no())) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_MN);
            }
            if (!SERegExpUtils.isEmail(req.getEmail_id())) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_EI);
            }
            if (req.getGender() == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_GENDER);
            }
            EducationCategoryBean educationDetails = req.getEducation_details();
            if (educationDetails != null) {
                validationService.validate(educationDetails);
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
            } else if (Boolean.TRUE.equals(user.getIs_verified())) {
                throw new CustomIllegalArgumentsException(ResponseCode.ALREADY_SIGNED_UP);
            }

            SEFilter filterE = new SEFilter(SEFilterType.AND);
            filterE.addClause(WhereClause.eq(Users.Fields.email_id, req.getEmail_id()));
            filterE.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            if (StringUtils.hasText(user.getId())) {
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
            user.setGender(req.getGender());
            user.setPassword(encode);
            user.setEducationDetails(educationDetails);

            users_Service.upsert(user.getId(), user, Defaults.SIGN_UP);

            String uuid = otpManagerService.send(req.getMobile_no(), ProcessType.SIGN_UP, Defaults.SIGN_UP);
            OTPResponse response = new OTPResponse();
            response.setReference_id(uuid);
            response.setEntity_id(user.getId());
            response.setProcess_type(ProcessType.SIGN_UP.name());
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
    public SEResponse verify(@RequestBody SERequest request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        try {
            log.info("/signup/verify:: API started");
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
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_REF_ID);
            }
            if (!StringUtils.hasText(entity_id)) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_ENTITY);
            }


            SEFilter filterCL = new SEFilter(SEFilterType.AND);
            filterCL.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, entity_id));
            filterCL.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Users users = users_Service.repoFindOne(filterCL);
            if (users == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.ENTITY_NOT_FOUND);
            }
            manageOtp.verify(users.getMobile_no(), reference_id, otp, ProcessType.SIGN_UP, Defaults.SIGN_UP);

            SEFilter filterR = new SEFilter(SEFilterType.AND);
            filterR.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, customer_signup_role));
            filterR.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Role role = roleService.repoFindOne(filterR);
            if (role == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.MSSING_CUST_DEF_ROLE);
            }
            users.setIs_verified(true);
            users.setRole_id(customer_signup_role);

            String guest_user_id = httpServletRequest.getHeader("req_user_id");
            if (StringUtils.hasText(guest_user_id)) {
                SEFilter filterGU = new SEFilter(SEFilterType.AND);
                filterGU.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, guest_user_id));
                filterGU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
                Users guestUser = users_Service.repoFindOne(filterGU);
                if (guestUser != null) {
                    users_Service.update(guest_user_id, users, users.getId());
                    signUpService.migrateCart(guest_user_id, users.getId());
                    signUpService.migrateAddressForCustomer(guest_user_id, users.getId());
                }
            }
            users_Service.update(users.getId(), users, Defaults.SIGN_UP);
            UsersBean usersBean = users_Service.validateAndGetUserInfo(users.getId());

            String cont = users.getFirst_name() + " " + users.getLast_name();
            MailBuilder builder = new MailBuilder();
            builder.setTo(users.getEmail_id());
            builder.setContent(cont);
            builder.setTemplate(MailTemplate.SIGN_UP_COMPLETED);
            emailSenderImpl.sendEmailHtmlTemplate(builder);
            setCookies(httpServletRequest, httpServletResponse, usersBean);
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
