package com.sorted.common.entity.mongo;

import com.sorted.common.entity.beans.Reward;
import com.sorted.common.entity.beans.RewardConditions;
import com.sorted.common.enums.RewardEvent;
import com.sorted.common.enums.RewardScope;
import com.sorted.common.enums.RewardValidityType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.util.List;

/**
 * Reward rules entity
 */
@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Builder
@Document("reward_rules")
public class RewardRulesEntity extends BaseMongoEntity<String> {

    @Field("reward_id")
    private String rewardId;
    @Field("reward_name")
    private String rewardName;
    @Field("reward_description")
    private String rewardDescription;
    @Field("triggering_event")
    private RewardEvent triggeringEvent; // e.g., ORDER_PLACED, SIGN_UP

    @Field("is_active")
    private boolean active;
    private Reward reward;
    @Field("reward_conditions")
    private RewardConditions rewardConditions;

    // Audience & Targeting
    @Field("scope")
    private RewardScope scope; // e.g., ALL_USERS, SPECIFIC_USERS

    @Field("audience_user_ids")
    private List<String> audienceUserIds; // For SPECIFIC_USERS scope

    // Stacking & Compatibility
    @Field("is_stackable")
    private Boolean isStackable = false; // Cannot be combined with other offers by default

    @Field("once_per_user")
    private Boolean oncePerUser = false;

    @Field("redeemed_user_ids")
    private List<String> redeemedUserIds;

    // Validity
    @Field("validity_type")
    private RewardValidityType validityType;

    @Field("valid_from")
    private LocalDate validFrom;

    @Field("valid_upto")
    private LocalDate validUpto;

    @Field("validity_in_days")
    private Integer validityInDays;

}
