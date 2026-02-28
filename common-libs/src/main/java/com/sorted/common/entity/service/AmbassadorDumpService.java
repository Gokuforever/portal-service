package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.AmbassadorDumpEntity;
import com.sorted.common.repository.mongo.AmbassadorDumpRepository;
import org.springframework.stereotype.Service;

@Service
public class AmbassadorDumpService extends GenericEntityServiceImpl<String, AmbassadorDumpEntity, AmbassadorDumpRepository> {


    @Override
    protected Class<AmbassadorDumpRepository> getRepoClass() {
        return AmbassadorDumpRepository.class;
    }

    @Override
    protected void validateBeforeCreate(AmbassadorDumpEntity inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeUpdate(String id, AmbassadorDumpEntity inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }
}
