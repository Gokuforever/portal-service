package com.sorted.portal.bl_services;

import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Role;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.RoleService;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.UserType;
import com.sorted.commons.helper.AggregationFilter.*;
import com.sorted.portal.response.beans.VerifiedUsersResBean;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@RestController
public class ManageUsers_BLService {

    private final Users_Service usersService;
    private final RoleService roleService;


    @GetMapping("/verified/users")
    public List<VerifiedUsersResBean> getAllVerifiedUsers() {

        SEFilter filterR = new SEFilter(SEFilterType.AND);
        filterR.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterR.addClause(WhereClause.eq(Role.Fields.user_type_id, UserType.CUSTOMER.getId()));

        Role role = roleService.repoFindOne(filterR);


        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter.addClause(WhereClause.eq(Users.Fields.is_verified, true));
        filter.addClause(WhereClause.eq(Users.Fields.role_id, role.getId()));

        OrderBy orderBy = new OrderBy(BaseMongoEntity.Fields.creation_date, SortOrder.DESC);

        filter.setOrderBy(orderBy);

        List<Users> users = usersService.repoFind(filter);

        List<VerifiedUsersResBean> verifiedUsersResBeans = new ArrayList<>();
        for (Users user : users) {
            VerifiedUsersResBean bean = VerifiedUsersResBean.builder()
                    .id(user.getId())
                    .firstName(user.getFirst_name())
                    .lastName(user.getLast_name())
                    .email(user.getEmail_id())
                    .mobile("91" + user.getMobile_no())
                    .createdAt(user.getCreation_date_str())
                    .build();

            verifiedUsersResBeans.add(bean);
        }
        return verifiedUsersResBeans;
    }
}
