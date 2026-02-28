package com.sorted.portal.bl_services;

import com.sorted.common.beans.EducationCategoryBean;
import com.sorted.common.constants.Defaults;
import com.sorted.common.entity.beans.AmbassadorDetails;
import com.sorted.common.entity.mongo.AmbassadorDumpEntity;
import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.CouponEntity;
import com.sorted.common.entity.mongo.Users;
import com.sorted.common.entity.service.AmbassadorDumpService;
import com.sorted.common.entity.service.CouponService;
import com.sorted.common.entity.service.Users_Service;
import com.sorted.common.enums.*;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import com.sorted.common.helper.MailBuilder;
import com.sorted.common.manage.otp.ManageOTPManagerService;
import com.sorted.common.manage.otp.ManageOtp;
import com.sorted.common.notifications.EmailSenderImpl;
import com.sorted.common.utils.Preconditions;
import com.sorted.common.utils.SERegExpUtils;
import com.sorted.portal.request.beans.VerifyAmbassador;
import com.sorted.portal.response.beans.AmbassadorOnboardingResponse;
import com.sorted.portal.service.EducationDetailsValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ambassador")
public class ManageAmbassador_BLService {

    private final Users_Service usersService;
    private final EducationDetailsValidationService validationService;
    private final ManageOTPManagerService manageOTPManagerService;
    private final ManageOtp manageOtp;
    private final AmbassadorDumpService ambassadorDumpService;
    private final EmailSenderImpl emailSender;
    private final CouponService couponService;
    @Value("${se.portal.customer.signup.role}")
    private String customer_signup_role;

    @PostMapping("/onboard")
    public AmbassadorOnboardingResponse onboard(@RequestBody AmbassadorDetails request) {
        Preconditions.check(StringUtils.hasText(request.getFirstname()), ResponseCode.MANDATE_FIRST_NAME);
        Preconditions.check(StringUtils.hasText(request.getLastname()), ResponseCode.MANDATE_LAST_NAME);
        Preconditions.check(StringUtils.hasText(request.getEmail()), ResponseCode.MISSING_EI);
        Preconditions.check(SERegExpUtils.isEmail(request.getEmail()), ResponseCode.INVALID_EI);
        Preconditions.check(StringUtils.hasText(request.getMobile()), ResponseCode.MISSING_MOBILE);
        Preconditions.check(SERegExpUtils.isMobileNo(request.getMobile()), ResponseCode.INVALID_MN);
        Preconditions.check(request.getGender() != null, ResponseCode.MANDATE_GENDER);
        EducationCategoryBean educationDetails = request.getEducationDetails();
        Preconditions.check(educationDetails != null, ResponseCode.MANDATE_EDUCATION_LEVEL_DETAILS);

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(Users.Fields.mobile_no, request.getMobile()));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        if (usersService.repoFindOne(filter) != null) {
            throw new CustomIllegalArgumentsException(ResponseCode.ALREADY_SIGNED_UP);
        }

        SEFilter filterE = new SEFilter(SEFilterType.AND);
        filterE.addClause(WhereClause.eq(Users.Fields.email_id, request.getEmail()));
        filterE.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        long emailCount = usersService.countByFilter(filterE);
        if (emailCount > 0) {
            throw new CustomIllegalArgumentsException(ResponseCode.DUPLICATE_EMAIL);
        }

        validationService.validate(educationDetails);
        String referenceId = manageOTPManagerService.send(request.getMobile(), ProcessType.AMBASSADOR_ONBOARDING, Defaults.AMBASSADOR_ONBOARDING);

        AmbassadorDumpEntity entity = AmbassadorDumpEntity.builder()
                .details(AmbassadorDetails.builder()
                        .firstname(request.getFirstname())
                        .lastname(request.getLastname())
                        .mobile(request.getMobile())
                        .gender(request.getGender())
                        .educationDetails(educationDetails)
                        .email(request.getEmail())
                        .build())
                .created(false)
                .build();

        AmbassadorDumpEntity ambassadorDumpEntity = ambassadorDumpService.create(entity, Defaults.AMBASSADOR_ONBOARDING);
        return AmbassadorOnboardingResponse.builder().processType(ProcessType.AMBASSADOR_ONBOARDING).entityId(ambassadorDumpEntity.getId()).referenceId(referenceId).build();
    }

    @PostMapping("/auth")
    public void auth(@RequestBody VerifyAmbassador request) {
        Preconditions.check(Objects.nonNull(request), ResponseCode.INVALID_REQ);
        Preconditions.check(Objects.nonNull(request.entityId()), ResponseCode.MISSING_ENTITY);

        Optional<AmbassadorDumpEntity> optional = ambassadorDumpService.findById(request.entityId());
        if (optional.isEmpty()) {
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }

        AmbassadorDumpEntity ambassadorDumpEntity = optional.get();
        if (Boolean.TRUE.equals(ambassadorDumpEntity.getCreated())) {
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }

        manageOtp.verify(request.mobileNo(), request.referenceId(), request.otp(), ProcessType.AMBASSADOR_ONBOARDING, Defaults.AMBASSADOR_ONBOARDING);

        AmbassadorDetails details = ambassadorDumpEntity.getDetails();

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(Users.Fields.mobile_no, details.getMobile()));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        if (usersService.repoFindOne(filter) != null) {
            throw new CustomIllegalArgumentsException(ResponseCode.ALREADY_SIGNED_UP);
        }

        SEFilter filterE = new SEFilter(SEFilterType.AND);
        filterE.addClause(WhereClause.eq(Users.Fields.email_id, details.getEmail()));
        filterE.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        long emailCount = usersService.countByFilter(filterE);
        if (emailCount > 0) {
            throw new CustomIllegalArgumentsException(ResponseCode.DUPLICATE_EMAIL);
        }

        Users users = new Users();
        users.setMobile_no(details.getMobile());
        users.setFirst_name(details.getFirstname());
        users.setLast_name(details.getLastname());
        users.setEmail_id(details.getEmail());
        users.setGender(details.getGender());
        users.setRole_id(customer_signup_role);
        users.setIs_verified(true);
        users.setAmbassador(true);
        users.setEducationDetails(details.getEducationDetails());

        usersService.create(users, Defaults.AMBASSADOR_ONBOARDING);

        Set<String> couponCodes;

        SEFilter filterC = new SEFilter(SEFilterType.AND);
        filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<CouponEntity> couponEntities = couponService.repoFind(filterC);
        if (!CollectionUtils.isEmpty(couponEntities)) {
            couponCodes = couponEntities.stream().map(CouponEntity::getCode).collect(Collectors.toSet());
        } else {
            couponCodes = new HashSet<>();
        }

        String couponCode = generateCouponCode(users.getFirst_name(), users.getLast_name(), couponCodes);

        CouponEntity entity = CouponEntity.builder()
                .code(couponCode)
                .name(couponCode)
                .startDate(LocalDate.now().atStartOfDay())
                .endDate(LocalDate.now().plusDays(31).atStartOfDay().minusMinutes(1))
                .description("Enjoy a flat 20% off (up to ₹1000) on your order, with free delivery.")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(0L)
                .discountPercentage(new BigDecimal(20))
                .couponScope(CouponScope.PUBLIC)
                .maxUses(100)
                .oncePerUser(true)
                .assignedToUsers(null)
                .maxDiscount(1000L)
                .minCartValue(0L)
                .active(true)
                .isDeliveryFree(true)
                .isVisibleInCart(false)
                .ambassadorId(users.getId())
                .build();


        couponService.create(entity, Defaults.RETOOL);

        MailBuilder builder = new MailBuilder();
        builder.setTo(users.getEmail_id());
        builder.setContent(users.getFirst_name());
        builder.setTemplate(MailTemplate.AMBASSADOR_WELCOME_MAIL);
        emailSender.sendEmailHtmlTemplate(builder);
    }

    public static String generateCouponCode(
            String firstName,
            String lastName,
            Set<String> usedCoupons) {

        String firstFour = getFirstFourChars(firstName);
        char lastInitial = getLastNameInitial(lastName);

        String baseCoupon = firstFour + "20";
        if (usedCoupons.add(baseCoupon)) {
            return baseCoupon;
        }

        String withLastInitial = firstFour + lastInitial + "20";
        if (usedCoupons.add(withLastInitial)) {
            return withLastInitial;
        }

        String uniqueCoupon;
        do {
            uniqueCoupon = firstFour + lastInitial + "20" + getUniqueSuffix();
        } while (!usedCoupons.add(uniqueCoupon));

        return uniqueCoupon;
    }

    private static String getFirstFourChars(String firstName) {
        if (firstName == null || firstName.trim().isEmpty()) {
            throw new IllegalArgumentException("First name cannot be null or empty");
        }

        String name = firstName.trim().toUpperCase();

        return name.length() >= 4
                ? name.substring(0, 4)
                : String.format("%-4s", name).replace(' ', 'X');
    }

    private static char getLastNameInitial(String lastName) {
        if (lastName == null || lastName.trim().isEmpty()) {
            return 'X';
        }
        return lastName.trim().toUpperCase().charAt(0);
    }

    private static String getUniqueSuffix() {
        String timestamp = Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        int random = ThreadLocalRandom.current().nextInt(100, 999);

        return timestamp.substring(timestamp.length() - 4) + random;
    }


}
