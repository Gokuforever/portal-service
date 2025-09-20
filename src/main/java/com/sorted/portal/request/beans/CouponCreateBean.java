package com.sorted.portal.request.beans;

import com.sorted.commons.enums.CouponScope;
import com.sorted.commons.enums.DiscountType;

import java.time.LocalDateTime;
import java.util.List;

public record CouponCreateBean(
        String code,
        String name,
        String description,
        DiscountType discountType,
        Long discountValue,
        Integer maxUsage,
        LocalDateTime startDate,
        LocalDateTime endDate,
        CouponScope couponScope,
        boolean oncePerUser,
        List<String> assignedToUsers,
        List<String> eligibleUserIds

) {
}
