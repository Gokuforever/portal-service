package com.sorted.common.entity.service;

import com.google.gson.Gson;
import com.sorted.common.beans.OTPResponse;
import com.sorted.common.beans.UsersBean;
import com.sorted.common.constants.Defaults;
import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.Role;
import com.sorted.common.entity.mongo.Seller;
import com.sorted.common.entity.mongo.Users;
import com.sorted.common.enums.*;
import com.sorted.common.enums.All_Status.User_Status;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import com.sorted.common.helper.ReqBaseBean;
import com.sorted.common.jwt.JwtTokenUtil;
import com.sorted.common.manage.otp.ManageOTPManagerService;
import com.sorted.common.repository.mongo.Users_Repository;
import com.sorted.common.utils.GsonUtils;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class Users_Service extends GenericEntityServiceImpl<String, Users, Users_Repository> {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private ManageOTPManagerService manageOTPManagerService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private Seller_Service seller_Service;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Override
    protected Class<Users_Repository> getRepoClass() {
        return Users_Repository.class;
    }

    @Override
    protected void validateBeforeCreate(Users inE) throws RuntimeException {
        inE.setStatus(User_Status.ACTIVE.getId());
    }

    @Override
    protected void validateBeforeUpdate(String id, Users inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }

    public OTPResponse validateUserForLogin(@NonNull String mobile_no, @NonNull String password) {
        SEFilter filterU = new SEFilter(SEFilterType.AND);
        filterU.addClause(WhereClause.eq(Users.Fields.mobile_no, mobile_no));
        filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Users user = this.repoFindOne(filterU);
        if (user == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.LOGIN_FAILED);
        }
        String pass = user.getPassword();
        if (!passwordEncoder.matches(password, pass)) {
            throw new CustomIllegalArgumentsException(ResponseCode.LOGIN_FAILED);
        }
        if (user.getStatus() != User_Status.ACTIVE.getId() || !Boolean.TRUE.equals(user.getIs_verified())) {
            throw new CustomIllegalArgumentsException(ResponseCode.USER_BLOCKED);
        }

        SEFilter filterR = new SEFilter(SEFilterType.AND);
        filterR.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, user.getRole_id()));
        filterR.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Role role = roleService.repoFindOne(filterR);
        if (role == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.ROLE_MISSING);
        }
        String uuid = manageOTPManagerService.send(mobile_no, ProcessType.SIGN_IN, Defaults.SIGN_IN);
        OTPResponse response = new OTPResponse();
        response.setReference_id(uuid);
        response.setProcess_type(ProcessType.SIGN_IN.name());
        response.setEntity_id(user.getId());
        return response;
    }

    public UsersBean validateAndGetUserInfo(String req_user_id) {
        return this.validateAndGetUserInfo(req_user_id, null);
    }

    private UsersBean validateAndGetUserInfo(@NonNull String req_user_id, String req_role_id) {
        try {
            log.info("validateAndGetUserInfo started.");
            SEFilter filterU = new SEFilter(SEFilterType.AND);
            filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req_user_id));
            if (StringUtils.hasText(req_role_id)) {
                filterU.addClause(WhereClause.eq(Users.Fields.role_id, req_role_id));
            }
            filterU.addClause(WhereClause.eq(Users.Fields.is_verified, true));
            filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Users users = this.repoFindOne(filterU);
            if (users == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.USER_NOT_FOUND);
            }
            if (!StringUtils.hasText(req_role_id)) {
                req_role_id = users.getRole_id();
            }
            SEFilter filterR = new SEFilter(SEFilterType.AND);
            filterR.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req_role_id));
            filterR.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Role role = roleService.repoFindOne(filterR);
            if (role == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.ROLE_MISSING);
            }

            Gson gson = GsonUtils.getGson();
            UsersBean usersBean = gson.fromJson(gson.toJson(users), UsersBean.class);
            usersBean.setPassword("");
            usersBean.setOld_password("");
            usersBean.setRole(role);
            this.validateHierarchy(role, usersBean);
            String[] tokens = jwtTokenUtil.generateToken(req_user_id);
            usersBean.setToken(tokens[0]);
            usersBean.setRefresh_token(tokens[1]);

            log.info("validateAndGetUserInfo ended.");
            return usersBean;
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("validateAndGetUserInfo:: error occerred:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    public UsersBean validateUserForActivity(@NonNull String req_user_id, @NonNull Activity... activity) {
        return this.validateUserForActivity(req_user_id, Permission.VIEW, activity);
    }

    public <T extends ReqBaseBean> UsersBean validateUserForActivity(@NonNull T bean, @NonNull Permission permission,
                                                                     @NonNull Activity... activity) {
        return this.validateUserForActivity(bean.getReq_user_id(), permission, activity);
    }

    public UsersBean validateUserForActivity(@NonNull String req_user_id, @NonNull Permission permission,
                                             @NonNull Activity... activity) {
        log.info("validateUserForActivity started.");
        SEFilter filterU = new SEFilter(SEFilterType.AND);
        filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req_user_id));
//		filterU.addClause(WhereClause.eq(Users.Fields.role_id, req_role_id));
        filterU.addClause(WhereClause.eq(Users.Fields.is_verified, true));
        filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Users users = this.repoFindOne(filterU);
        if (users == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.USER_NOT_FOUND);
        }
        SEFilter filterR = new SEFilter(SEFilterType.AND);
        filterR.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, users.getRole_id()));
        filterR.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Role role = roleService.repoFindOne(filterR);
        if (role == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.ROLE_MISSING);
        }
        if (CollectionUtils.isEmpty(role.getRole_permissions())) {
            throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
        }
        boolean hasAccess = false;
        for (Activity act : activity) {
            if (act == Activity.USER_PROFILE) {
                hasAccess = true;
                break;
            }
            hasAccess = role.getRole_permissions().stream().anyMatch(
                    e -> (e.getActivity_id() == act.getId() && e.getPermissions().contains(permission.getId())));
            if (hasAccess) {
                break;
            }
        }
        if (!hasAccess) {
            throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
        }
        Gson gson = GsonUtils.getGson();
        UsersBean usersBean = gson.fromJson(gson.toJson(users), UsersBean.class);
        usersBean.setRole(role);

        this.validateHierarchy(role, usersBean);
        log.info("validateUserForActivity ended.");
        return usersBean;
    }

    private void validateHierarchy(Role role, UsersBean usersBean) {
        if (Objects.requireNonNull(role.getUser_type()) == UserType.SELLER) {
            if (!StringUtils.hasText(role.getSeller_id())) {
                throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }
            SEFilter filterS = new SEFilter(SEFilterType.AND);
            filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, role.getSeller_id()));
            filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Seller seller = seller_Service.repoFindOne(filterS);
            if (seller == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }
            usersBean.setSeller(seller);
        }
    }

    public boolean isSeller(String req_user_id) {
        Optional<Users> optional = this.findById(req_user_id);
        if (optional.isEmpty()) {
            return false;
        }
        Users users = optional.get();
        return isSellerByRoleId(users.getRole_id());
    }

    private boolean isSellerByRoleId(String roleId) {
        if (!StringUtils.hasText(roleId)) {
            return false;
        }
        Optional<Role> optional1 = roleService.findById(roleId);
        if (optional1.isEmpty()) {
            return false;
        }
        Role role = optional1.get();
        return role.getUser_type().equals(UserType.SELLER);
    }

    public boolean isSeller(Users users) {
        return isSellerByRoleId(users.getRole_id());

    }

    public boolean isSellerByMobile(String mobileNo) {
        SEFilter filterU = new SEFilter(SEFilterType.AND);
        filterU.addClause(WhereClause.eq(Users.Fields.mobile_no, mobileNo));
        filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        Users users = this.repoFindOne(filterU);
        if (users == null) {
            return false;
        }
        return isSellerByRoleId(users.getRole_id());
    }

}
