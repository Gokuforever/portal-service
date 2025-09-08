package com.sorted.portal.service;

import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.ProcessType;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.helper.AggregationFilter;
import com.sorted.commons.manage.otp.ManageOtp;
import com.sorted.commons.utils.Preconditions;
import com.sorted.commons.utils.SERegExpUtils;
import com.sorted.portal.request.beans.AuthV2Bean;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final ManageOtp manageOtp;
    private final Users_Service users_Service;
    @Value("${se.portal.customer.signup.role}")
    private String customer_signup_role;
    @Value("${se.guest.role_id}")
    private String guest_role_id;
    private final SignUpService signUpService;

    public Users verifyAuth(AuthV2Bean auth, ProcessType processType, HttpServletRequest httpServletRequest) {
        Preconditions.check(StringUtils.hasText(auth.mobileNo()), ResponseCode.MISSING_MN);
        Preconditions.check(SERegExpUtils.isMobileNo(auth.mobileNo()), ResponseCode.INVALID_MN);
        Preconditions.check(StringUtils.hasText(auth.referenceId()), ResponseCode.MISSING_REF_ID);
        Preconditions.check(StringUtils.hasText(auth.otp()), ResponseCode.MISSING_OTP);
        Preconditions.check(SERegExpUtils.isOtp(auth.otp()), ResponseCode.INVALID_OTP);

        manageOtp.verify(auth.mobileNo(), auth.referenceId(), auth.otp(), processType, Defaults.AUTH);
        AggregationFilter.SEFilter filter = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filter.addClause(AggregationFilter.WhereClause.eq(Users.Fields.mobile_no, auth.mobileNo()));
        filter.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        Users user = users_Service.repoFindOne(filter);
        if (user == null) {
            user = new Users();
            user.setMobile_no(auth.mobileNo());
            user = users_Service.create(user, Defaults.AUTH);
        }
        user.setRole_id(customer_signup_role);
        user.setIs_verified(true);
        Users users = users_Service.update(user.getId(), user, Defaults.AUTH);
        String guest_user_id = httpServletRequest.getHeader("req_user_id");
        if (StringUtils.hasText(guest_user_id)) {
            AggregationFilter.SEFilter filterGU = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
            filterGU.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.id, guest_user_id));
            filterGU.addClause(AggregationFilter.WhereClause.eq(Users.Fields.role_id, guest_role_id));
            filterGU.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            Users guestUser = users_Service.repoFindOne(filterGU);
            if (guestUser != null) {
                signUpService.migrateCart(guest_user_id, users.getId());
                signUpService.migrateAddressForCustomer(guest_user_id, users.getId());
            }
        }
        return users;
    }
}
