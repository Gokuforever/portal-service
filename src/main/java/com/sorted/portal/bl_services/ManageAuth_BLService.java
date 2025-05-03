package com.sorted.portal.bl_services;

import com.sorted.commons.beans.OTPResponse;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.All_Status.User_Status;
import com.sorted.commons.enums.EntityDetails;
import com.sorted.commons.enums.ProcessType;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.manage.otp.ManageOtp;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.PasswordValidatorUtils;
import com.sorted.commons.utils.SERegExpUtils;
import com.sorted.portal.annotation.RateLimited;
import com.sorted.portal.request.beans.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@RequestMapping("/auth")
@RestController
public class ManageAuth_BLService {

    private final Users_Service users_Service;
    private final ManageOtp manageOtp;
    private final int reset_window;
    private final PasswordEncoder passwordEncoder;

    public ManageAuth_BLService(
            Users_Service users_Service,
            ManageOtp manageOtp,
            @Value("${se.portal.password.reset.window.in_minutes}") int reset_window) {
        this.users_Service = users_Service;
        this.manageOtp = manageOtp;
        this.reset_window = reset_window;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @PostMapping("/signin")
    @RateLimited(value = 5.0) // 5 requests per second
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
    @RateLimited(value = 5.0)
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
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_REF_ID);
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
    @RateLimited(value = 5.0)
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
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_REF_ID);
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

    @PostMapping("/forgotPass")
    @RateLimited(value = 5.0)
    public SEResponse forgotPass(@RequestBody SERequest request) {
        try {
            ForgotPassBean req = request.getGenericRequestDataObject(ForgotPassBean.class);
            if (!StringUtils.hasText(req.getMobile_no())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_MOBILE);
            }
            SEFilter filterU = new SEFilter(SEFilterType.AND);
            filterU.addClause(WhereClause.eq(Users.Fields.mobile_no, req.getMobile_no()));
            filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Users users = users_Service.repoFindOne(filterU);
            if (users == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.USER_NOT_FOUND);
            }
            if (!Boolean.TRUE.equals(users.getIs_verified())) {
                throw new CustomIllegalArgumentsException(ResponseCode.USER_NOT_FOUND);
            }
            if (User_Status.ACTIVE.getId() != users.getStatus()) {
                throw new CustomIllegalArgumentsException(ResponseCode.USER_BLOCKED);
            }
            String uuid = manageOtp.send(users.getMobile_no(), users.getId(), ProcessType.FORGOT_PASS,
                    EntityDetails.USERS, users.getId());
            OTPResponse response = new OTPResponse();
            response.setReference_id(uuid);
            response.setProcess_type(ProcessType.FORGOT_PASS.name());
            response.setEntity_id(users.getId());
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

    @PostMapping("/forgotPass/verifyOtp")
    @RateLimited(value = 5.0)
    public SEResponse forgotpassVerifyOtp(@RequestBody SERequest request) {
        try {
            log.info("auth/forgotpass/verifyOtp:: API started!");
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
            manageOtp.verify(EntityDetails.USERS, reference_id, otp, entity_id, ProcessType.FORGOT_PASS,
                    Defaults.FORGOT_PASS);
            SEFilter filterU = new SEFilter(SEFilterType.AND);
            filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, entity_id));
            filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Users users = users_Service.repoFindOne(filterU);
            if (users == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.USER_NOT_FOUND);
            }
            if (!Boolean.TRUE.equals(users.getIs_verified())) {
                throw new CustomIllegalArgumentsException(ResponseCode.USER_NOT_FOUND);
            }
            if (User_Status.ACTIVE.getId() != users.getStatus()) {
                throw new CustomIllegalArgumentsException(ResponseCode.USER_BLOCKED);
            }
            users.setUuid(UUID.randomUUID().toString());
            users.setReset_pass_request_expiry(LocalDateTime.now().plusMinutes(reset_window));
            users_Service.update(users.getId(), users, users.getId());
            OTPResponse response = new OTPResponse();
            response.setReference_id(users.getUuid());
            response.setEntity_id(users.getId());
            return SEResponse.getBasicSuccessResponseObject(response, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("auth/verifyOtp:: exception occurred");
            log.error("auth/verifyOtp:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/forgotPass/changePassword")
    public SEResponse changePassword(@RequestBody SERequest request) {
        try {
            ForgotPassBean req = request.getGenericRequestDataObject(ForgotPassBean.class);
            if (!StringUtils.hasText(req.getPassword())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PASS);
            }
            if (!StringUtils.hasText(req.getEntity_id())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_ENTITY);
            }
            if (!StringUtils.hasText(req.getReference_id())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_REF_ID);
            }

            SEFilter filterU = new SEFilter(SEFilterType.AND);
            filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getEntity_id()));
            filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Users users = users_Service.repoFindOne(filterU);
            if (users == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.USER_NOT_FOUND);
            }
            if (!Boolean.TRUE.equals(users.getIs_verified())) {
                throw new CustomIllegalArgumentsException(ResponseCode.USER_NOT_FOUND);
            }
            if (User_Status.ACTIVE.getId() != users.getStatus()) {
                throw new CustomIllegalArgumentsException(ResponseCode.USER_BLOCKED);
            }
            LocalDateTime now = LocalDateTime.now();
            if (users.getReset_pass_request_expiry() == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.SESSION_EXPIRED);
            }
            if (now.isAfter(users.getReset_pass_request_expiry())) {
                throw new CustomIllegalArgumentsException(ResponseCode.SESSION_EXPIRED);
            }
            if (!users.getUuid().equals(req.getReference_id())) {
                throw new CustomIllegalArgumentsException(ResponseCode.SESSION_EXPIRED);
            }
            String password = req.getPassword().trim();
            PasswordValidatorUtils.validatePassword(password);
            String encode = passwordEncoder.encode(password);
            String db_password = users.getPassword();
            if (passwordEncoder.matches(password, db_password)) {
                throw new CustomIllegalArgumentsException(ResponseCode.BR_OLD_PASSWORD);
            }
            if (!users.isPass_changed()) {
                users.setPass_changed(true);
            }

            users.setOld_password(db_password);
            users.setPassword(encode);
            users.setReset_pass_request_expiry(now);
            users.setUuid(null);
            users_Service.update(users.getId(), users, users.getId());

            log.info("signin:: API ended");
            return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("signin:: exception occurred");
            log.error("signin:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/refresh")
    public SEResponse refresh(@RequestBody SERequest request, HttpServletRequest servletRequest) {
        try {
            BlankReqBean req = request.getGenericRequestDataObject(BlankReqBean.class);
            CommonUtils.extractHeaders(servletRequest, req);
            return null;
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("signin:: exception occurred");
            log.error("signin:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }
}
