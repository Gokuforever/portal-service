package com.sorted.common.beans;

import com.sorted.common.enums.UsageStatus;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@FieldNameConstants
@Data
@Builder
public class CouponUsage {
    @Field("user_id")
    private String userId;
    @Field("order_id")
    private String orderId;
    @Field("discount_amount")
    private Long discountAmount; // Actual discount applied
    @Field("used_at")
    private LocalDateTime usedAt;
    @Field("status")
    private UsageStatus status;
}
