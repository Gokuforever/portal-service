package com.sorted.portal.bl_services;

import com.sorted.commons.beans.OTPResponse;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Cart;
import com.sorted.commons.entity.mongo.Role;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.Cart_Service;
import com.sorted.commons.entity.service.RoleService;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.EntityDetails;
import com.sorted.commons.enums.ProcessType;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.enums.Semester;
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
import com.sorted.portal.request.beans.VerifyOtpBean;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class ManageSignUp_BLService {

    private final ManageOtp manageOtp;
    private final Users_Service users_Service;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final String customer_signup_role;
    private final Cart_Service cart_Service;

    // Constructor Injection
    public ManageSignUp_BLService(ManageOtp manageOtp, Users_Service users_Service, RoleService roleService, PasswordEncoder passwordEncoder,
                                  @Value("${se.portal.customer.signup.role}") String customer_signup_role, Cart_Service cart_Service) {
        this.manageOtp = manageOtp;
        this.users_Service = users_Service;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
        this.customer_signup_role = customer_signup_role;
        this.cart_Service = cart_Service;
    }

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
            if (!StringUtils.hasText(req.getBranch())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_BRANCH_NAME);
            }
            boolean isOtherBranch = req.getBranch().equalsIgnoreCase("other");
            if (isOtherBranch && !StringUtils.hasText(req.getDesc())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SEMESTER_DESC);
            }
            if (!StringUtils.hasText(req.getSemester())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SEMISTER);
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
            if (!SERegExpUtils.standardTextValidation(req.getBranch())) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_BRANCH);
            }
            Semester semester = Semester.getByAlias(req.getSemester());
            if (semester == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SEMISTER);
            }
            if (!StringUtils.hasText(req.getCollege())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_COLLEGE_NAME);
            }
            if (!SERegExpUtils.standardTextValidation(req.getCollege())) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_COLLEGE_NAME);
            }
            if (req.getGender() == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_GENDER);
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
            user.setBranch(req.getBranch());
            user.setBranch_desc(isOtherBranch ? req.getDesc() : null);
            user.setSemester(semester.getAlias());
            user.setCollege(StringUtils.hasText(req.getCollege()) ? req.getCollege() : null);
            user.setGender(req.getGender());
            user.setPassword(encode);

            user = users_Service.upsert(user.getId(), user, Defaults.SIGN_UP);

            String uuid = manageOtp.send(req.getMobile_no(), user.getId(), ProcessType.SIGN_UP, EntityDetails.USERS, Defaults.SIGN_UP);
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
    public SEResponse verify(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
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

            manageOtp.verify(EntityDetails.USERS, reference_id, otp, entity_id, ProcessType.SIGN_UP, Defaults.SIGN_UP);

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
            users.setIs_verified(true);
            users.setRole_id(customer_signup_role);
            users_Service.update(users.getId(), users, Defaults.SIGN_UP);

            Cart new_cart = new Cart();
            new_cart.setUser_id(users.getId());
            String req_user_id = httpServletRequest.getHeader("req_user_id");
            if (StringUtils.hasText(req_user_id)) {
                SEFilter filterC = new SEFilter(SEFilterType.AND);
                filterC.addClause(WhereClause.eq(Cart.Fields.user_id, req_user_id));
                filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

                Cart cart = cart_Service.repoFindOne(filterC);
                if (cart != null) {
                    new_cart.setCart_items(cart.getCart_items());
                }
            }

            cart_Service.create(new_cart, Defaults.SIGN_UP);

            UsersBean usersBean = users_Service.validateAndGetUserInfo(users.getId());

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
