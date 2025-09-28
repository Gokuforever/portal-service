package com.sorted.portal.bl_services;

import com.sorted.commons.beans.ReferralDetails;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Role;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.RoleService;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.UserType;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.utils.ReferralUtility;
import com.sorted.portal.request.beans.CreateReferralBean;
import com.sorted.portal.request.beans.MakeAmbassadorBean;
import com.sorted.portal.response.beans.AmbassadorDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/referral")
public class ManageReferral_BLService {

    private final ReferralUtility referralUtility;
    private final Users_Service usersService;
    private final RoleService roleService;

    @PostMapping("/create")
    public void createReferral(@RequestBody CreateReferralBean request) {

        referralUtility.createReferral(request.userId(), request.code());
    }

    @GetMapping("/fetch/referred-users")
    public List<ReferralDetails> getReferredUsers() {
        return referralUtility.getReferredUsers();
    }

    @GetMapping("/fetch/all-ambassadors")
    public List<AmbassadorDetails> getAllAmbassadors() {

        SEFilter filterRole = new SEFilter(SEFilterType.AND);
        filterRole.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterRole.addClause(WhereClause.eq(Role.Fields.user_type_id, UserType.CUSTOMER.getId()));

        Role role = roleService.repoFindOne(filterRole);

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(Users.Fields.ambassador, true));
        filter.addClause(WhereClause.eq(Users.Fields.role_id, role.getId()));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Users> ambassadors = usersService.repoFind(filter);

        if (CollectionUtils.isEmpty(ambassadors)) {
            return Collections.emptyList();
        }

        return ambassadors.stream().map(this::mapResponse).toList();
    }

    private AmbassadorDetails mapResponse(Users ambassador) {
        return AmbassadorDetails.builder()
                .mobileNo("+91" + ambassador.getMobile_no())
                .name(StringUtils.hasText(ambassador.getFirst_name()) ? ambassador.getFirst_name() + " " + ambassador.getLast_name() : "")
                .build();
    }

    @GetMapping("/fetch/all-non-ambassadors")
    public List<AmbassadorDetails> getAllNonAmbassadors() {

        SEFilter filterRole = new SEFilter(SEFilterType.AND);
        filterRole.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterRole.addClause(WhereClause.eq(Role.Fields.user_type_id, UserType.CUSTOMER.getId()));

        Role role = roleService.repoFindOne(filterRole);

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(Users.Fields.role_id, role.getId()));
        filter.addClause(WhereClause.eq(Users.Fields.ambassador, false));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Users> ambassadors = usersService.repoFind(filter);

        if (CollectionUtils.isEmpty(ambassadors)) {
            return Collections.emptyList();
        }

        return ambassadors.stream().map(this::mapResponse).toList();
    }

    @PostMapping("/make/ambassador")
    public void makeAmbassador(@RequestBody MakeAmbassadorBean request) {

        SEFilter filterRole = new SEFilter(SEFilterType.AND);
        filterRole.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterRole.addClause(WhereClause.eq(Role.Fields.user_type_id, UserType.CUSTOMER.getId()));

        Role role = roleService.repoFindOne(filterRole);

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, request.userId()));
        filter.addClause(WhereClause.eq(Users.Fields.role_id, role.getId()));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        Users user = usersService.repoFindOne(filter);
        
        user.setAmbassador(true);
        usersService.update(user.getId(), user, Defaults.RETOOL);
    }

}
