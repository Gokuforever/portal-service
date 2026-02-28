package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.RecommendersEntity;
import com.sorted.common.repository.mongo.RecommendersRepository;
import org.springframework.stereotype.Service;

@Service
public class RecommendersService extends GenericEntityServiceImpl<String, RecommendersEntity, RecommendersRepository> {
    @Override
    protected Class<RecommendersRepository> getRepoClass() {
        return RecommendersRepository.class;
    }

    @Override
    protected void validateBeforeCreate(RecommendersEntity inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeUpdate(String id, RecommendersEntity inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }
}
