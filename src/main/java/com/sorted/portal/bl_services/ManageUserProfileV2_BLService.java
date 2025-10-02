package com.sorted.portal.bl_services;

import com.sorted.commons.beans.EducationCategoryBean;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.ProcessType;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.AccessDeniedException;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.manage.otp.ManageOTPManagerService;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.Preconditions;
import com.sorted.commons.utils.ReferralUtility;
import com.sorted.commons.utils.SERegExpUtils;
import com.sorted.portal.request.beans.CompleteUserProfile;
import com.sorted.portal.response.beans.CompleteProfileRes;
import com.sorted.portal.service.AuthService;
import com.sorted.portal.service.EducationDetailsValidationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v2/profile")
@RequiredArgsConstructor
public class ManageUserProfileV2_BLService {

    private final Users_Service usersService;
    private final EducationDetailsValidationService validationService;
    private final ManageOTPManagerService manageOtp;
    private final AuthService authService;
    private final ReferralUtility referralUtility;

    @PostMapping("/complete")
    public CompleteProfileRes completeProfile(@RequestBody CompleteUserProfile request, HttpServletRequest httpServletRequest) {
        CommonUtils.extractHeaders(httpServletRequest, request);
        UsersBean usersBean = usersService.validateUserForActivity(request.getReq_user_id(), Activity.USER_PROFILE);

        switch (usersBean.getRole().getUser_type()) {
            case CUSTOMER:
                if (request.getAuth() != null) {
                    authService.verifyAuth(request.getAuth(), ProcessType.PROFILE, httpServletRequest);
                } else {
                    Preconditions.check(StringUtils.hasText(request.getMobile()), ResponseCode.MISSING_MOBILE);
                    Preconditions.check(SERegExpUtils.isMobileNo(request.getMobile()), ResponseCode.INVALID_MN);
                    if (!usersBean.getMobile_no().equals(request.getMobile())) {
                        return verifyUniqueMobile(request);
                    }
                }
                break;
            case GUEST:
                if (request.getAuth() != null) {
                    authService.verifyAuth(request.getAuth(), ProcessType.PROFILE, httpServletRequest);
                } else {
                    Preconditions.check(StringUtils.hasText(request.getMobile()), ResponseCode.MISSING_MOBILE);
                    Preconditions.check(SERegExpUtils.isMobileNo(request.getMobile()), ResponseCode.INVALID_MN);
                    return verifyUniqueMobile(request);
                }
                break;
            default:
                throw new AccessDeniedException();
        }

        validateRequest(request);

        Users users = usersService.findById(usersBean.getId()).get();
        users.setFirst_name(request.getFirstname());
        users.setLast_name(request.getLastname());
        users.setEmail_id(request.getEmail());
        users.setGender(request.getGender());
        users.setEducationDetails(request.getEducationDetails());

        usersService.update(users.getId(), users, users.getId());

        if (StringUtils.hasText(request.getReferralCode())) {
            referralUtility.refer(request.getReq_user_id(), request.getReferralCode());
        }
        UsersBean userInfo = usersService.validateAndGetUserInfo(users.getId());
        return CompleteProfileRes.builder().userInfo(userInfo).build();
    }

    private void validateRequest(CompleteUserProfile request) {
        Preconditions.check(StringUtils.hasText(request.getFirstname()), ResponseCode.MANDATE_FIRST_NAME);
        Preconditions.check(StringUtils.hasText(request.getLastname()), ResponseCode.MANDATE_LAST_NAME);
        Preconditions.check(StringUtils.hasText(request.getEmail()), ResponseCode.MISSING_EI);
        Preconditions.check(SERegExpUtils.isEmail(request.getEmail()), ResponseCode.INVALID_EI);
        SEFilter filterE = new SEFilter(SEFilterType.AND);
        filterE.addClause(WhereClause.eq(Users.Fields.email_id, request.getEmail()));
        filterE.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        if (StringUtils.hasText(request.getReq_user_id())) {
            filterE.addClause(WhereClause.notEq(BaseMongoEntity.Fields.id, request.getReq_user_id()));
        }

        long emailCount = usersService.countByFilter(filterE);
        if (emailCount > 0) {
            throw new CustomIllegalArgumentsException(ResponseCode.DUPLICATE_EMAIL);
        }

        Preconditions.check(request.getGender() != null, ResponseCode.MANDATE_GENDER);
        EducationCategoryBean educationDetails = request.getEducationDetails();
        Preconditions.check(educationDetails != null, ResponseCode.MANDATE_EDUCATION_LEVEL_DETAILS);
        validationService.validate(educationDetails);

        if (StringUtils.hasText(request.getReferralCode())) {
            referralUtility.validateReferralCode(request.getReq_user_id(), request.getReferralCode());
        }
    }

    private CompleteProfileRes verifyUniqueMobile(CompleteUserProfile request) {

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(Users.Fields.mobile_no, request.getMobile()));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        if (usersService.repoFindOne(filter) != null) {
            throw new CustomIllegalArgumentsException(ResponseCode.ALREADY_SIGNED_UP);
        }

        validateRequest(request);
        String referenceId = manageOtp.send(request.getMobile(), ProcessType.PROFILE, request.getReq_user_id());
        return CompleteProfileRes.builder().referenceId(referenceId).otpVerificationRequired(true).build();
    }

    @GetMapping("/fetch")
    public UsersBean fetchProfile(HttpServletRequest httpServletRequest) {
        String req_user_id = httpServletRequest.getHeader("req_user_id");
        if (!StringUtils.hasText(req_user_id)) {
            throw new AccessDeniedException();
        }
        return usersService.validateAndGetUserInfo(req_user_id);
    }
}
