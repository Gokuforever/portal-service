package com.sorted.common.utils;

import com.sorted.common.beans.ReferralDetails;
import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.ReferralEntity;
import com.sorted.common.entity.mongo.Users;
import com.sorted.common.entity.service.ReferralService;
import com.sorted.common.entity.service.Users_Service;
import com.sorted.common.enums.ResponseCode;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import com.sorted.common.exceptions.InvalidReferralCodeException;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReferralUtility {

    private final ReferralService service;
    private final Users_Service usersService;

    public void createReferral(String userId, String code) {

        if (!SERegExpUtils.isAlphaNumeric(code)) {
            throw new InvalidReferralCodeException();
        }

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(ReferralEntity.Fields.code, code));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        long count = service.countByFilter(filter);
        if (count > 0) {
            throw new CustomIllegalArgumentsException(ResponseCode.REFERRAL_CODE_EXISTS);
        }

        SEFilter filter1 = new SEFilter(SEFilterType.AND);
        filter1.addClause(WhereClause.eq(ReferralEntity.Fields.userId, userId));
        filter1.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        ReferralEntity referralEntity = service.repoFindOne(filter1);
        if (referralEntity != null) {
            throw new CustomIllegalArgumentsException("Referral code already exists: " + referralEntity.getCode());
        }
        ReferralEntity referral = ReferralEntity.builder()
                .code(code)
                .userId(userId)
                .count(0)
                .active(true)
                .build();
        service.create(referral, userId);
    }

    public void validateReferralCode(String code, String userId) {
        if (!SERegExpUtils.isAlphaNumeric(code)) {
            throw new InvalidReferralCodeException();
        }

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(ReferralEntity.Fields.code, code));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        ReferralEntity referralEntity = service.repoFindOne(filter);
        if (referralEntity == null) {
            throw new InvalidReferralCodeException();
        }

        SEFilter filter2 = new SEFilter(SEFilterType.AND);
        filter2.addClause(WhereClause.in(ReferralEntity.Fields.users, List.of(userId)));
        filter2.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        ReferralEntity alreadyReferred = service.repoFindOne(filter2);
        if (alreadyReferred == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.REFERRAL_NOT_APPLICABLE);
        }
    }

    public void refer(String userId, String code) {

        if (!SERegExpUtils.isAlphaNumeric(code)) {
            throw new InvalidReferralCodeException();
        }

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(ReferralEntity.Fields.code, code));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        ReferralEntity referralEntity = service.repoFindOne(filter);
        if (referralEntity == null) {
            throw new InvalidReferralCodeException();
        }

        SEFilter filter2 = new SEFilter(SEFilterType.AND);
        filter2.addClause(WhereClause.in(ReferralEntity.Fields.users, List.of(userId)));
        filter2.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        ReferralEntity alreadyReferred = service.repoFindOne(filter2);
        if (alreadyReferred == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.REFERRAL_NOT_APPLICABLE);
        }

        referralEntity.setCount(referralEntity.getCount() + 1);
        referralEntity.getUsers().add(userId);
        service.update(referralEntity.getId(), referralEntity, userId);
    }

    public List<ReferralDetails> getReferredUsers() {

        List<ReferralDetails> referralDetails = new ArrayList<>();
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<ReferralEntity> referralEntities = service.repoFind(filter);

        if (CollectionUtils.isEmpty(referralEntities)) {
            return referralDetails;
        }

        List<String> userIds = new ArrayList<>(referralEntities.stream().flatMap(ref -> ref.getUsers().stream()).toList());
        userIds.addAll(referralEntities.stream().map(ReferralEntity::getUserId).toList());

        SEFilter filterUser = new SEFilter(SEFilterType.AND);
        filterUser.addClause(WhereClause.in(BaseMongoEntity.Fields.id, userIds));
        filterUser.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Users> users = usersService.repoFind(filterUser);

        Map<String, Users> usersMap = users.stream().collect(Collectors.toMap(Users::getId, u -> u));

        for (ReferralEntity referralEntity : referralEntities) {

            Users user = usersMap.getOrDefault(referralEntity.getUserId(), null);
            if (user == null) {
                continue;
            }

            List<String> referredUsers = new ArrayList<>();
            if (!CollectionUtils.isEmpty(referralEntity.getUsers())) {
                referredUsers = referralEntity.getUsers().stream().map(e -> {
                    Users u = usersMap.getOrDefault(e, null);
                    if (u == null || !StringUtils.hasText(u.getFirst_name()) || !StringUtils.hasText(u.getLast_name())) {
                        return null;
                    }
                    return u.getFirst_name() + " " + u.getLast_name();
                }).toList();
            }
            ReferralDetails.builder()
                    .mobileNo("")
                    .name(user.getFirst_name() + " " + user.getLast_name())
                    .referredUsers(referredUsers)
                    .count(referralEntity.getCount())
                    .build();
        }

        return referralDetails;
    }
}
