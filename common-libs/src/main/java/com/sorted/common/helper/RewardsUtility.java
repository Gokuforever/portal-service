package com.sorted.common.helper;

import com.sorted.common.beans.CreateRewardRuleBean;
import com.sorted.common.constants.Defaults;
import com.sorted.common.entity.mongo.RewardRulesEntity;
import com.sorted.common.entity.service.RewardRulesService;
import com.sorted.common.entity.service.WalletService;
import com.sorted.common.enums.*;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import com.sorted.common.utils.CommonUtils;
import com.sorted.common.utils.Preconditions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Slf4j
public class RewardsUtility {

    private final RewardRulesService rewardRulesService;

    private final WalletService walletService;

    public void createRewardRule(CreateRewardRuleBean bean) {
        RewardRulesEntity entity = buildEntityFromBean(bean);
        rewardRulesService.create(entity, Defaults.RETOOL);
    }

    public void creditOnEvent(String userId, RewardEvent rewardEvent) {
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(RewardRulesEntity.Fields.triggeringEvent, rewardEvent.name()));
        filter.addClause(WhereClause.eq(RewardRulesEntity.Fields.active, true));

        RewardRulesEntity rewardRules = rewardRulesService.repoFindOne(filter);
        if (rewardRules == null) {
            log.info("No active reward rule found.");
            return;
        }

        // Check for validity
        LocalDate today = LocalDate.now();
        if (rewardRules.getValidityType() != null) {
            if (rewardRules.getValidityType().equals(RewardValidityType.DATE_RANGE)) {
                if (rewardRules.getValidFrom() != null && rewardRules.getValidUpto() != null &&
                        (today.isBefore(rewardRules.getValidFrom()) || today.isAfter(rewardRules.getValidUpto()))) {
                    log.info("reward rule is not active today. Rule ID: {}", rewardRules.getRewardId());
                    return;
                }
            }
        }

        // Check if the reward can be claimed only once and if the user has already claimed it
        if (rewardRules.getOncePerUser() && !CollectionUtils.isEmpty(rewardRules.getRedeemedUserIds()) && rewardRules.getRedeemedUserIds().contains(userId)) {
            log.info("User {} has already redeemed the reward.", userId);
            return;
        }

        // Validate the reward type and value
        Long creditAmount = rewardRules.getReward().getValue();
        if (rewardRules.getReward().getType() != RewardType.WALLET_CREDIT || creditAmount <= 0) {
            log.error("Invalid reward configuration. Reward must be of type WALLET_CREDIT with a positive value. Rule ID: {}", rewardRules.getRewardId());
            return;
        }

        LocalDate expiresAt = null;
        if (rewardRules.getValidityType() != null) {
            switch (rewardRules.getValidityType()) {
                case DATE_RANGE:
                    expiresAt = rewardRules.getValidUpto();
                    break;
                case DAYS_FROM_CREDIT:
                    if (rewardRules.getValidityInDays() != null && rewardRules.getValidityInDays() > 0) {
                        expiresAt = LocalDate.now().plusDays(rewardRules.getValidityInDays());
                    }
                    break;
            }
        }

        walletService.creditAmount(userId, creditAmount, WalletTxnSource.SIGNUP_REWARD, expiresAt);

        // If oncePerUser is true, update the redeemed user list
        if (rewardRules.getOncePerUser()) {
            if (CollectionUtils.isEmpty(rewardRules.getRedeemedUserIds())) {
                rewardRules.setRedeemedUserIds(new ArrayList<>());
            }
            rewardRules.getRedeemedUserIds().add(userId);
            rewardRulesService.update(rewardRules.getId(), rewardRules, WalletTxnSource.SIGNUP_REWARD.name());
            log.info("Successfully credited reward to user {} and updated redeemed list.", userId);
        }
    }

    private RewardRulesEntity buildEntityFromBean(CreateRewardRuleBean bean) {
        validateBeforeCreate(bean);

        return RewardRulesEntity.builder()
                .rewardId(UUID.randomUUID().toString())
                .rewardName(bean.rewardName())
                .rewardDescription(bean.rewardDescription())
                .triggeringEvent(bean.triggeringEvent())
                .active(bean.active())
                .reward(bean.reward())
                .rewardConditions(bean.rewardConditions())
                .scope(bean.scope())
                .audienceUserIds(bean.audienceUserIds())
                .isStackable(bean.isStackable())
                .oncePerUser(bean.oncePerUser())
                .validityType(bean.validityType())
                .validFrom(bean.validFrom())
                .validUpto(bean.validUpto())
                .validityInDays(bean.validityInDays())
                .build();
    }

    private void validateBeforeCreate(CreateRewardRuleBean bean) {
        Preconditions.check(bean != null, ResponseCode.INVALID_REQ);
        Preconditions.check(StringUtils.hasText(bean.rewardName()), ResponseCode.MISSING_REWARD_NAME);
        Preconditions.check(bean.triggeringEvent() != null, ResponseCode.MISSING_REWARD_TRIGGER_EVENT);
        Preconditions.check(bean.scope() != null, ResponseCode.MISSING_REWARD_SCOPE);
        Preconditions.check(bean.reward() != null, ResponseCode.MISSING_REWARD_DETAILS);

        // Validate validity details
        Preconditions.check(bean.validityType() != null, ResponseCode.MISSING_REWARD_VALIDITY_TYPE);
        switch (bean.validityType()) {
            case DATE_RANGE:
                Preconditions.check(bean.validFrom() != null && bean.validUpto() != null, ResponseCode.MISSING_REWARD_VALIDITY_DATES);
                Preconditions.check(!bean.validFrom().isAfter(bean.validUpto()), ResponseCode.INVALID_REWARD_VALIDITY_DATES);
                break;
            case DAYS_FROM_CREDIT:
                Preconditions.check(bean.validityInDays() != null && bean.validityInDays() > 0, ResponseCode.INVALID_REWARD_VALIDITY_DAYS);
                break;
        }

        if (bean.scope() == RewardScope.SPECIFIC_USERS) {
            Preconditions.check(!CollectionUtils.isEmpty(bean.audienceUserIds()), ResponseCode.MISSING_AUDIENCE_FOR_SPECIFIC_SCOPE);
        }

        RewardType rewardType = bean.reward().getType();
        Long rewardValue = bean.reward().getValue();
        Preconditions.check(rewardType != null, ResponseCode.MISSING_REWARD_TYPE);

        if (rewardType == RewardType.PERCENTAGE) {
            Preconditions.check(rewardValue != null, ResponseCode.MISSING_REWARD_VALUE);
            BigDecimal rewardValueBD = CommonUtils.paiseToRupee(rewardValue);
            Preconditions.check(rewardValueBD.compareTo(BigDecimal.ZERO) > 0 && rewardValueBD.compareTo(new BigDecimal("100")) <= 0, ResponseCode.INVALID_REWARD_PERCENTAGE_VALUE);
        } else if (rewardType == RewardType.FREE_ITEM) {
            Preconditions.check(StringUtils.hasText(bean.reward().getProductId()), ResponseCode.MISSING_PRODUCT_ID);
            Preconditions.check(bean.reward().getQuantity() != null, ResponseCode.MISSING_QUANTITY);
            Preconditions.check(bean.reward().getQuantity() > 0, ResponseCode.INVALID_QUANTITY);
        } else {
            Preconditions.check(rewardValue != null, ResponseCode.MISSING_REWARD_VALUE);
            Preconditions.check(rewardValue > 0, ResponseCode.INVALID_REWARD_VALUE);
        }
    }
}
