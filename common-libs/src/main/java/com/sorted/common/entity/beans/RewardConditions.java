package com.sorted.common.entity.beans;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Builder
@Data
@FieldNameConstants
public class RewardConditions {

    @Field("min_order_value")
    private Long minOrderValue;
    @Field("max_order_value")
    private Long maxOrderValue;
    @Field("is_first_order")
    private boolean isFirstOrder;
    @Field("is_valid_from")
    private LocalDateTime isValidFrom;
    @Field("is_valid_to")
    private LocalDateTime isValidTo;
}
