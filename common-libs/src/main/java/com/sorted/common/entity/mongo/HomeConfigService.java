package com.sorted.common.entity.mongo;

import com.sorted.common.entity.service.GenericEntityServiceImpl;
import com.sorted.common.repository.mongo.HomeConfigRepository;
import org.springframework.stereotype.Service;

@Service
public class HomeConfigService extends GenericEntityServiceImpl<String, HomeConfig, HomeConfigRepository> {
    @Override
    protected Class<HomeConfigRepository> getRepoClass() {
        return HomeConfigRepository.class;
    }

    @Override
    protected void validateBeforeCreate(HomeConfig inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeUpdate(String id, HomeConfig inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }
}
