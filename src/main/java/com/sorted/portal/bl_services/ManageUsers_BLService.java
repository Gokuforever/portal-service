package com.sorted.portal.bl_services;

import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.Users_Service;
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



    @GetMapping("/verified/users")
    public List<VerifiedUsersResBean> getAllVerifiedUsers(){

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter.addClause(WhereClause.eq(Users.Fields.is_verified, true));
        List<Users> users = usersService.repoFind(filter);

        List<VerifiedUsersResBean> verifiedUsersResBeans = new ArrayList<>();
        for (Users user : users) {
            VerifiedUsersResBean bean = VerifiedUsersResBean.builder()
                    .id(user.getId())
                    .firstName(user.getFirst_name())
                    .lastName(user.getLast_name())
                    .email(user.getEmail_id())
                    .mobile(user.getMobile_no())
                    .createdAt(user.getCreation_date_str())
                    .build();

            verifiedUsersResBeans.add(bean);
        }
        return verifiedUsersResBeans;
    }
}
