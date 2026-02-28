package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.AssetsEntity;
import com.sorted.common.repository.mongo.AssetsRepository;
import org.springframework.stereotype.Service;

@Service
public class AssetsService extends GenericEntityServiceImpl<String, AssetsEntity, AssetsRepository> {
    @Override
    protected Class<AssetsRepository> getRepoClass() {
        return AssetsRepository.class;
    }

    @Override
    protected void validateBeforeCreate(AssetsEntity inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeUpdate(String id, AssetsEntity inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }
}
