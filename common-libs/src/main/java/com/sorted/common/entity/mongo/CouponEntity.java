package com.sorted.common.entity.mongo;

import com.sorted.common.beans.CouponUsage;
import com.sorted.common.enums.CouponScope;
import com.sorted.common.enums.DiscountType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "coupon")
@Builder
public class CouponEntity extends BaseMongoEntity<String> {

    private String code;
    private String name;
    private String description;
    @Field("discount_type")
    private DiscountType discountType;
    @Field("discount_value")
    private Long discountValue;
    @Field("discount_percentage")
    private BigDecimal discountPercentage;
    @Field("max_uses")
    private Integer maxUses;
    @Field("used_count")
    private Integer usedCount = 0;
    @Field("max_uses_per_user")
    private Integer maxUsesPerUser = 1;
    @Field("start_dates")
    private LocalDateTime startDate;
    @Field("end_date")
    private LocalDateTime endDate;
    @Field("min_cart_value")
    private Long minCartValue;
    /*
    @Field("applicable_product_ids")
    private List<String> applicableProductIds;
    @Field("applicable_category_ids")
    private List<String> applicableCategoryIds;
    @Field("excluded_product_ids")
    private List<String> excludedProductIds;
    */
    @Field("eligible_user_ids")
    private List<String> eligibleUserIds;
    @Field("couponScope")
    private CouponScope couponScope;
    @Field("is_once_per_user")
    private boolean oncePerUser = false; // If true, each user can only use this coupon once regardless of maxUsesPerUser
    @Field("is_active")
    private boolean active;
    @Field("assigned_to_users")
    private List<String> assignedToUsers;
    @Field("max_discount")
    private Long maxDiscount;
    @Field("coupon_usages")
    private List<CouponUsage> couponUsages;
    @Field("is_visible_in_cart")
    private boolean isVisibleInCart;
    @Field("is_delivery_free")
    private boolean isDeliveryFree;
    @Field("ambassador_id")
    private String ambassadorId;

}
