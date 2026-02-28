package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.ZoneEntity;
import com.sorted.common.repository.mongo.ZoneRepository;
import org.springframework.stereotype.Service;

@Service
public class ZoneService extends GenericEntityServiceImpl<String, ZoneEntity, ZoneRepository> {

    @Override
    protected Class<ZoneRepository> getRepoClass() {
        return ZoneRepository.class;
    }

    @Override
    protected void validateBeforeCreate(ZoneEntity inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeUpdate(String id, ZoneEntity inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }
}
