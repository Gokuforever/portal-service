package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.NotifyRestockEntity;
import com.sorted.common.repository.mongo.NotifyRestockRepository;
import org.springframework.stereotype.Service;

@Service
public class NotifyRestockService extends GenericEntityServiceImpl<String, NotifyRestockEntity, NotifyRestockRepository>{
    @Override
    protected Class<NotifyRestockRepository> getRepoClass() {
        return NotifyRestockRepository.class;
    }

    @Override
    protected void validateBeforeCreate(NotifyRestockEntity inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeUpdate(String id, NotifyRestockEntity inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }
}
