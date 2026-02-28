package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.CouponEntity;
import com.sorted.common.repository.mongo.CouponRepository;
import org.springframework.stereotype.Service;

@Service
public class CouponService extends GenericEntityServiceImpl<String, CouponEntity, CouponRepository> {
    @Override
    protected Class<CouponRepository> getRepoClass() {
        return CouponRepository.class;
    }

    @Override
    protected void validateBeforeCreate(CouponEntity inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeUpdate(String id, CouponEntity inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }
}
