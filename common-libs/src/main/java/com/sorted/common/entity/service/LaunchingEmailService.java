package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.LaunchingEmail;
import com.sorted.common.repository.mongo.LaunchingEmailRepository;
import org.springframework.stereotype.Service;

@Service
public class LaunchingEmailService extends GenericEntityServiceImpl<String, LaunchingEmail, LaunchingEmailRepository> {
    @Override
    protected Class<LaunchingEmailRepository> getRepoClass() {
        return LaunchingEmailRepository.class;
    }

    @Override
    protected void validateBeforeCreate(LaunchingEmail inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeUpdate(String id, LaunchingEmail inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }
}
