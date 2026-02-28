package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.RewardRulesEntity;
import com.sorted.common.repository.mongo.RewardRulesRepository;
import com.sorted.common.utils.CommonUtils;
import org.springframework.stereotype.Service;

@Service
public class RewardRulesService extends GenericEntityServiceImpl<String, RewardRulesEntity, RewardRulesRepository> {
    @Override
    protected Class<RewardRulesRepository> getRepoClass() {
        return RewardRulesRepository.class;
    }

    @Override
    protected void validateBeforeCreate(RewardRulesEntity inE) throws RuntimeException {
        String code = CommonUtils.createCode("RR");
        inE.setRewardId(code);
    }

    @Override
    protected void validateBeforeUpdate(String id, RewardRulesEntity inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }
}
