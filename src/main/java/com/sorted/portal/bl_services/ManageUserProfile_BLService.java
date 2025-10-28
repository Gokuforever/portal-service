package com.sorted.portal.bl_services;

import com.sorted.commons.beans.OTPResponse;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.All_Status.User_Status;
import com.sorted.commons.enums.ProcessType;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.manage.otp.ManageOTPManagerService;
import com.sorted.commons.manage.otp.ManageOtp;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.PasswordValidatorUtils;
import com.sorted.commons.utils.SERegExpUtils;
import com.sorted.portal.request.beans.BlankReqBean;
import com.sorted.portal.request.beans.ChangePassword;
import com.sorted.portal.request.beans.VerifyOtpBean;
import com.sorted.portal.response.beans.UserProfileBean;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@RequestMapping("/profile")
@RestController
@RequiredArgsConstructor
public class ManageUserProfile_BLService {

    private final Users_Service users_Service;
    private final ManageOtp manageOtp;
    private final ManageOTPManagerService manageOTPManagerService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    @Value("${se.portal.password.reset.window.in_minutes}")
    private int resetWindow;


    @GetMapping("/fetch")
    public SEResponse fetch(HttpServletRequest servletRequest) {
        try {
            log.info("/profile/fetch:: API started");
            String req_user_id = servletRequest.getHeader("req_user_id");
            if (!StringUtils.hasText(req_user_id)) {
                throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }
            UsersBean usersBean = users_Service.validateUserForActivity(req_user_id, Activity.USER_PROFILE);
            UserProfileBean bean = getUserProfileBean(usersBean);

            log.info("/profile/fetch:: API ended");
            return SEResponse.getBasicSuccessResponseObject(bean, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/profile/fetch:: exception occurred");
            log.error("/profile/fetch:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @NotNull
    private static UserProfileBean getUserProfileBean(UsersBean usersBean) {

        UserProfileBean bean = new UserProfileBean();
        bean.setFirst_name(usersBean.getFirst_name());
        bean.setLast_name(usersBean.getLast_name());
        bean.setMobile_no(usersBean.getMobile_no());
        bean.setEmail_id(usersBean.getEmail_id());
        bean.setProperties(usersBean.getProperties());
        bean.setGender(usersBean.getGender());
        bean.setUser_type(usersBean.getRole().getUser_type());
        bean.setUser_type_id(usersBean.getRole().getUser_type().getId());
        bean.setEnable_referral(!StringUtils.hasText(usersBean.getEmail_id()));
        return bean;
    }

    @PostMapping("/updatePass")
    public SEResponse updatePass(@RequestBody SERequest request, HttpServletRequest servletRequest) {
        try {
            BlankReqBean req = request.getGenericRequestDataObject(BlankReqBean.class);
            CommonUtils.extractHeaders(servletRequest, req);

            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.USER_PROFILE);
            if (!Boolean.TRUE.equals(usersBean.getIs_verified())) {
                throw new CustomIllegalArgumentsException(ResponseCode.USER_NOT_FOUND);
            }
            if (User_Status.ACTIVE.getId() != usersBean.getStatus()) {
                throw new CustomIllegalArgumentsException(ResponseCode.USER_BLOCKED);
            }
            String uuid = manageOTPManagerService.send(usersBean.getMobile_no(), ProcessType.UPDATE_PASS, usersBean.getId());
            OTPResponse response = new OTPResponse();
            response.setReference_id(uuid);
            response.setProcess_type(ProcessType.UPDATE_PASS.name());
            response.setEntity_id(usersBean.getId());
            log.info("/updatePass:: API ended");
            return SEResponse.getBasicSuccessResponseObject(response, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/updatePass:: exception occurred");
            log.error("/updatePass:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/updatePass/verifyOtp")
    public SEResponse updatePassVerifyOtp(@RequestBody SERequest request, HttpServletRequest servletRequest) {
        try {
            log.info("/updatePass/verifyOtp:: API started!");
            VerifyOtpBean req = request.getGenericRequestDataObject(VerifyOtpBean.class);
            CommonUtils.extractHeaders(servletRequest, req);
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.USER_PROFILE);
            String otp = req.getOtp();
            String reference_id = req.getReference_id();
            String entity_id = usersBean.getId();
            if (!StringUtils.hasText(otp)) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_OTP);
            }
            if (!SERegExpUtils.isOtp(otp)) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_OTP);
            }
            if (!StringUtils.hasText(reference_id)) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_REF_ID);
            }
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
            manageOtp.verify(users.getMobile_no(), reference_id, otp, ProcessType.UPDATE_PASS, Defaults.UPDATE_PASS);
            users.setUuid(UUID.randomUUID().toString());
            users.setReset_pass_request_expiry(LocalDateTime.now().plusMinutes(resetWindow));
            users_Service.update(users.getId(), users, users.getId());
            OTPResponse response = new OTPResponse();
            response.setReference_id(users.getUuid());
            response.setEntity_id(users.getId());
            return SEResponse.getBasicSuccessResponseObject(response, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/updatePass/verifyOtp:: exception occurred");
            log.error("/updatePass/verifyOtp:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/updatePass/changePassword")
    public SEResponse changePassword(@RequestBody SERequest request, HttpServletRequest servletRequest) {
        try {
            log.info("/updatePass/changePassword:: API started");
            ChangePassword req = request.getGenericRequestDataObject(ChangePassword.class);
            CommonUtils.extractHeaders(servletRequest, req);

            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.USER_PROFILE);
            if (!StringUtils.hasText(req.getPassword())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PASS);
            }
            if (!StringUtils.hasText(req.getReference_id())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_REF_ID);
            }
            LocalDateTime now = LocalDateTime.now();
            if (usersBean.getReset_pass_request_expiry() == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.SESSION_EXPIRED);
            }
            if (now.isAfter(usersBean.getReset_pass_request_expiry())) {
                throw new CustomIllegalArgumentsException(ResponseCode.SESSION_EXPIRED);
            }
            if (!usersBean.getUuid().equals(req.getReference_id())) {
                throw new CustomIllegalArgumentsException(ResponseCode.SESSION_EXPIRED);
            }

            String password = req.getPassword().trim();
            PasswordValidatorUtils.validatePassword(password);
            String encode = passwordEncoder.encode(password);

            SEFilter filterU = new SEFilter(SEFilterType.AND);
            filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getReq_user_id()));
            filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Users users = users_Service.repoFindOne(filterU);
            if (users == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.USER_NOT_FOUND);
            }
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

            log.info("/updatePass/changePassword:: API ended");
            return SEResponse.getBasicSuccessResponseObject("", ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/updatePass/changePassword:: exception occurred");
            log.error("/updatePass/changePassword:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }
}
