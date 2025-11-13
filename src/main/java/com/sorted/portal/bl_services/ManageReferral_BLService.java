package com.sorted.portal.bl_services;

import com.sorted.commons.beans.ReferralDetails;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.service.*;
import com.sorted.commons.enums.MailTemplate;
import com.sorted.commons.enums.OrderStatus;
import com.sorted.commons.enums.UserType;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.MailBuilder;
import com.sorted.commons.notifications.EmailSenderImpl;
import com.sorted.commons.utils.ReferralUtility;
import com.sorted.portal.request.beans.CreateReferralBean;
import com.sorted.portal.request.beans.MakeAmbassadorBean;
import com.sorted.portal.request.beans.ReferralCodeDetails;
import com.sorted.portal.response.beans.AmbassadorDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@RequestMapping("/referral")
public class ManageReferral_BLService {

    private final ReferralUtility referralUtility;
    private final Users_Service usersService;
    private final RoleService roleService;
    private final ReferralService referralService;
    private final CouponService couponService;
    private final Order_Details_Service orderDetailsService;
    private final EmailSenderImpl emailSender;

    @GetMapping("/codes/all")
    public List<ReferralCodeDetails> getAllReferrals() {
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter.addClause(WhereClause.eq(ReferralEntity.Fields.active, true));

        List<ReferralEntity> referralEntities = referralService.repoFind(filter);
        if (CollectionUtils.isEmpty(referralEntities)) {
            return Collections.emptyList();
        }

        List<String> userIds = referralEntities.stream().map(ReferralEntity::getUserId).toList();

        SEFilter filterU = new SEFilter(SEFilterType.AND);
        filterU.addClause(WhereClause.in(BaseMongoEntity.Fields.id, userIds));
        filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Users> users = usersService.repoFind(filterU);

        Map<String, Users> usersmap = users.stream().collect(Collectors.toMap(Users::getId, u -> u));
        List<ReferralCodeDetails> response = new ArrayList<>();
        for (ReferralEntity referralEntity : referralEntities) {
            Users user = usersmap.getOrDefault(referralEntity.getUserId(), null);
            if (user == null) {
                continue;
            }
            response.add(getReferralCodeDetails(referralEntity, user));
        }
        return response;

    }

    private static ReferralCodeDetails getReferralCodeDetails(ReferralEntity referralEntity, Users user) {
        return ReferralCodeDetails.builder()
                .id(referralEntity.getId())
                .code(referralEntity.getCode())
                .mobile("91" + user.getMobile_no())
                .name(StringUtils.hasText(user.getFirst_name()) ? user.getFirst_name() + " " + user.getLast_name() : "")
                .count(referralEntity.getCount())
                .build();
    }

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

        Map<String, Long> signupCount = new HashMap<>();

        SEFilter filterRU = new SEFilter(SEFilterType.AND);
        filterRU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterRU.addClause(WhereClause.in(Users.Fields.ambassadorId, ambassadors.stream().map(Users::getId).toList()));

        List<Users> referredUsers = usersService.repoFind(filterRU);
        if (!CollectionUtils.isEmpty(referredUsers)) {
            signupCount.putAll(referredUsers.stream().collect(Collectors.groupingBy(Users::getAmbassadorId, Collectors.counting())));
        }

        Map<String, Integer> countMap = ambassadors.stream().collect(Collectors.toMap(Users::getId, u -> 0));
        Map<String, String> mapCoupons = new HashMap<>();

        SEFilter filterC = new SEFilter(SEFilterType.AND);
        filterC.addClause(WhereClause.in(CouponEntity.Fields.ambassadorId, ambassadors.stream().map(Users::getId).toList()));
        filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<CouponEntity> couponEntities = couponService.repoFind(filterC);
        if (!CollectionUtils.isEmpty(couponEntities)) {
            mapCoupons.putAll(couponEntities.stream().collect(Collectors.toMap(CouponEntity::getAmbassadorId, CouponEntity::getCode)));
            SEFilter filterOD = new SEFilter(SEFilterType.AND);
            filterOD.addClause(WhereClause.eq(Order_Details.Fields.status_id, OrderStatus.DELIVERED.getId()));
            filterOD.addClause(WhereClause.in(Order_Details.Fields.coupon_code, mapCoupons.values().stream().toList()));

            List<Order_Details> orderDetails = orderDetailsService.repoFind(filterOD);
            if (!CollectionUtils.isEmpty(orderDetails)) {
                Map<String, List<Order_Details>> mapOrderDetails = orderDetails.stream().collect(Collectors.groupingBy(Order_Details::getCoupon_code));
                for (Map.Entry<String, List<Order_Details>> entry : mapOrderDetails.entrySet()) {
                    countMap.put(entry.getKey(), entry.getValue().size());
                }
            }
        }

        return ambassadors.stream().map(u -> mapResponse(u, countMap, mapCoupons, signupCount)).toList();
    }

    private AmbassadorDetails mapResponse(Users ambassador) {
        return AmbassadorDetails.builder()
                .id(ambassador.getId())
                .mobileNo("+91" + ambassador.getMobile_no())
                .name(StringUtils.hasText(ambassador.getFirst_name()) ? ambassador.getFirst_name() + " " + ambassador.getLast_name() : "")
                .emailId(ambassador.getEmail_id())
                .build();
    }

    private AmbassadorDetails mapResponse(Users ambassador, Map<String, Integer> countMap, Map<String, String> mapCoupons, Map<String, Long> signupCount) {
        return AmbassadorDetails.builder()
                .id(ambassador.getId())
                .mobileNo("+91" + ambassador.getMobile_no())
                .name(StringUtils.hasText(ambassador.getFirst_name()) ? ambassador.getFirst_name() + " " + ambassador.getLast_name() : "")
                .referredCount(countMap.get(ambassador.getId()))
                .signupCount(signupCount.getOrDefault(ambassador.getId(), 0L))
                .couponCode(mapCoupons.getOrDefault(ambassador.getId(), null))
                .emailId(ambassador.getEmail_id())
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
        filter.addClause(WhereClause.isNotNull(Users.Fields.email_id));
        filter.addClause(WhereClause.isNotNull(Users.Fields.first_name));
        filter.addClause(WhereClause.isNotNull(Users.Fields.last_name));
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

        MailBuilder builder = new MailBuilder();
        builder.setTo(user.getEmail_id());
        builder.setContent(user.getFirst_name());
        builder.setTemplate(MailTemplate.AMBASSADOR_WELCOME_MAIL);
        emailSender.sendEmailHtmlTemplate(builder);
    }

    @PostMapping("/remove/ambassador")
    public void removeAmbassador(@RequestBody MakeAmbassadorBean request) {

        SEFilter filterRole = new SEFilter(SEFilterType.AND);
        filterRole.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterRole.addClause(WhereClause.eq(Role.Fields.user_type_id, UserType.CUSTOMER.getId()));

        Role role = roleService.repoFindOne(filterRole);

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, request.userId()));
        filter.addClause(WhereClause.eq(Users.Fields.role_id, role.getId()));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        Users user = usersService.repoFindOne(filter);

        user.setAmbassador(false);
        usersService.update(user.getId(), user, Defaults.RETOOL);
    }

}
