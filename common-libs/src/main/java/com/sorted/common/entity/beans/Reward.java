package com.sorted.common.entity.beans;

import com.sorted.common.enums.RewardType;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Field;

@Builder
@FieldNameConstants
@Data
public class Reward {

    private RewardType type;

    private Long value;

    @Field("min_amount")
    private Long minAmount;

    @Field("max_amount")
    private Long maxAmount;

    @Field("include_free_delivery")
    private boolean includeFreeDelivery;

    // Specific to FREE_ITEM reward type
    @Field("product_id")
    private String productId;

    @Field("quantity")
    private Integer quantity;

}
