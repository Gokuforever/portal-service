package com.sorted.portal.bl_services;

import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.CouponEntity;
import com.sorted.commons.entity.mongo.Role;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.CouponService;
import com.sorted.commons.entity.service.RoleService;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.CouponScope;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.enums.UserType;
import com.sorted.commons.exceptions.AccessDeniedException;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.exceptions.InvalidDiscountType;
import com.sorted.commons.helper.AggregationFilter.*;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.Preconditions;
import com.sorted.portal.request.beans.CreateCouponBean;
import com.sorted.portal.response.beans.ActiveCoupon;
import com.sorted.portal.response.beans.UserInfoBean;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@RequiredArgsConstructor
@RestController
@RequestMapping("/coupon")
public class ManageCoupon_BLService {

    private final Users_Service usersService;
    private final RoleService roleService;
    private final CouponService couponService;

    @GetMapping("/customers")
    public List<UserInfoBean> getCustomers() {

        SEFilter filterR = new SEFilter(SEFilterType.AND);
        filterR.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterR.addClause(WhereClause.eq(Role.Fields.user_type_id, UserType.CUSTOMER.getId()));

        Role role = roleService.repoFindOne(filterR);

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter.addClause(WhereClause.eq(Users.Fields.role_id, role.getId()));

        OrderBy orderBy = new OrderBy(BaseMongoEntity.Fields.creation_date, SortOrder.DESC);

        filter.setOrderBy(orderBy);

        List<Users> users = usersService.repoFind(filter);
        return users.stream().map(this::getUserInfoBean).toList();
    }

    private UserInfoBean getUserInfoBean(Users user) {
        return UserInfoBean.builder()
                .id(user.getId())
                .mobile("91" + user.getMobile_no())
                .firstName(user.getFirst_name())
                .lastName(user.getLast_name())
                .email(user.getEmail_id())
                .build();
    }

    @PostMapping("/create")
    public void create(@RequestBody CreateCouponBean request) {
        validate(request);

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter.addClause(WhereClause.eq(CouponEntity.Fields.code, request.code()));

        long count = couponService.countByFilter(filter);

        if (count > 0) {
            throw new CustomIllegalArgumentsException(ResponseCode.COUPON_CODE_EXISTS);
        }

        CouponEntity entity = CouponEntity.builder()
                .code(request.code())
                .name(request.name())
                .startDate(LocalDate.parse(request.startDate()).atStartOfDay())
                .endDate(LocalDate.parse(request.endDate()).plusDays(1).atStartOfDay().minusMinutes(1))
                .description(request.description())
                .discountType(request.discountType())
                .discountValue(CommonUtils.rupeeToPaise(request.discountValue()))
                .discountPercentage(request.discountPercentage())
                .couponScope(request.couponScope())
                .maxUses(request.maxUsage())
                .oncePerUser(request.oncePerUser())
                .assignedToUsers(request.assignedToUsers())
                .maxDiscount(CommonUtils.rupeeToPaise(request.maxDiscount()))
                .minCartValue(request.minCartValue() == null ? 0L : CommonUtils.rupeeToPaise(request.minCartValue()))
                .build();
        couponService.create(entity, Defaults.RETOOL);
    }

    private static void validate(CreateCouponBean request) {
        try {
            log.info("validate:: API started");
            log.info("request: {}", request);
            Preconditions.check(request != null, ResponseCode.INVALID_REQ);
            Preconditions.check(StringUtils.hasText(request.code()), ResponseCode.MISSING_COUPON_CODE);
            Preconditions.check(StringUtils.hasText(request.name()), ResponseCode.MISSING_COUPON_NAME);
            Preconditions.check(StringUtils.hasText(request.startDate()), ResponseCode.MISSING_COUPON_START_DATE);
            Preconditions.check(StringUtils.hasText(request.endDate()), ResponseCode.MISSING_COUPON_END_DATE);
            LocalDate startDate = LocalDate.parse(request.startDate());
            LocalDate endDate = LocalDate.parse(request.endDate());
            Preconditions.check(startDate.isAfter(LocalDate.now().minusDays(1)), ResponseCode.MISSING_COUPON_START_DATE);
            Preconditions.check(startDate.isBefore(endDate), ResponseCode.MISSING_COUPON_END_DATE);
            Preconditions.check(StringUtils.hasText(request.description()), ResponseCode.MISSING_COUPON_DESCRIPTION);
            Preconditions.check(request.discountType() != null, ResponseCode.MISSING_COUPON_DISCOUNT_TYPE);

            switch (request.discountType()) {
                case FIXED -> {
                    Preconditions.check(request.discountValue() != null, ResponseCode.MISSING_COUPON_DISCOUNT_VALUE);
                    Preconditions.check(request.discountValue().compareTo(BigDecimal.ZERO) > 0, ResponseCode.INVALID_COUPON_DISCOUNT_VALUE);
                }
                case PERCENTAGE -> {
                    Preconditions.check(request.discountPercentage() != null, ResponseCode.MISSING_COUPON_DISCOUNT_PERCENTAGE);
                    Preconditions.check(request.discountPercentage().compareTo(BigDecimal.ZERO) > 0 && request.discountPercentage().compareTo(new BigDecimal(100)) <= 0, ResponseCode.INVALID_COUPON_DISCOUNT_PERCENTAGE);
                    if (request.maxDiscount() != null) {
                        Preconditions.check(request.maxDiscount().compareTo(BigDecimal.ZERO) >= 0, ResponseCode.INVALID_COUPON_MAX_DISCOUNT);
                    }
                }
                default -> throw new InvalidDiscountType();
            }

            Preconditions.check(request.couponScope() != null, ResponseCode.MISSING_COUPON_SCOPE);
            CouponScope couponScope = request.couponScope();
            if (couponScope == CouponScope.USER_SPECIFIC) {
                Preconditions.check(!CollectionUtils.isEmpty(request.assignedToUsers()), ResponseCode.MISSING_COUPON_ASSIGNED_TO_USERS);
            }
        } catch (CustomIllegalArgumentsException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @GetMapping("/getActiveCoupons")
    public List<ActiveCoupon> getActiveCoupons(HttpServletRequest request) {
        String req_user_id = request.getHeader("req_user_id");
        Preconditions.check(StringUtils.hasText(req_user_id), new AccessDeniedException());
        UsersBean usersBean = usersService.validateUserForActivity(req_user_id, Activity.HOME);

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter.addClause(WhereClause.lte(CouponEntity.Fields.startDate, LocalDateTime.now()));
        filter.addClause(WhereClause.gte(CouponEntity.Fields.endDate, LocalDate.now().plusDays(1).atStartOfDay().minusMinutes(1)));

        List<CouponEntity> coupons = couponService.repoFind(filter);

        List<ActiveCoupon> activeCoupons = new ArrayList<>();
        for (CouponEntity coupon : coupons) {
            if (coupon.isOncePerUser()) {
                if (!CollectionUtils.isEmpty(coupon.getCouponUsages())) {
                    boolean match = coupon.getCouponUsages().stream().anyMatch(usage -> usage.getUserId().equals(usersBean.getId()));
                    if (match) {
                        continue;
                    }
                }
                if (coupon.getCouponScope().equals(CouponScope.USER_SPECIFIC)) {
                    if (CollectionUtils.isEmpty(coupon.getAssignedToUsers())) {
                        continue;
                    }
                    boolean match = coupon.getAssignedToUsers().stream().anyMatch(user -> user.equals(usersBean.getId()));
                    if (!match) {
                        continue;
                    }
                }
                ActiveCoupon activeCoupon = ActiveCoupon.builder()
                        .couponCode(coupon.getCode())
                        .description(coupon.getDescription())
                        .discountType(coupon.getDiscountType())
                        .discountValue(CommonUtils.paiseToRupee(coupon.getDiscountValue()))
                        .discountPercentage(coupon.getDiscountPercentage())
                        .startDate(coupon.getStartDate().toLocalDate())
                        .endDate(coupon.getEndDate().toLocalDate())
                        .name(coupon.getName())
                        .build();
                activeCoupons.add(activeCoupon);
            }
        }
        return activeCoupons;
    }
}
