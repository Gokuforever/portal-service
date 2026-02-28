package com.sorted.common.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.common.entity.beans.Reward;
import com.sorted.common.entity.beans.RewardConditions;
import com.sorted.common.enums.RewardEvent;
import com.sorted.common.enums.RewardScope;
import com.sorted.common.enums.RewardValidityType;

import java.time.LocalDate;
import java.util.List;

public record CreateRewardRuleBean(
        @JsonProperty("reward_name")
        String rewardName,
        @JsonProperty("reward_description")
        String rewardDescription,
        @JsonProperty("triggering_event")
        RewardEvent triggeringEvent,
        boolean active,
        Reward reward,
        @JsonProperty("reward_conditions")
        RewardConditions rewardConditions,
        RewardScope scope,
        @JsonProperty("audience_user_ids")
        List<String> audienceUserIds,
        @JsonProperty("is_stackable")
        boolean isStackable,
        @JsonProperty("once_per_user")
        boolean oncePerUser,
        @JsonProperty("validity_type")
        RewardValidityType validityType,
        @JsonProperty("valid_from")
        LocalDate validFrom,
        @JsonProperty("valid_upto")
        LocalDate validUpto,
        @JsonProperty("validity_in_days")
        Integer validityInDays
) {
}
