package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.DemandingPincode;
import com.sorted.common.repository.mongo.DemandingPincode_Repository;
import org.springframework.stereotype.Service;

@Service
public class DemandingPincodeService extends GenericEntityServiceImpl<String, DemandingPincode, DemandingPincode_Repository> {

    @Override
    protected Class<DemandingPincode_Repository> getRepoClass() {
        return DemandingPincode_Repository.class;
    }

    @Override
    protected void validateBeforeCreate(DemandingPincode inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeUpdate(String id, DemandingPincode inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }

    public void storeDemandingPincode(String pincode, String user_id) {
        DemandingPincode demandingPincode = DemandingPincode.builder()
                .user_id(user_id)
                .pincode(pincode)
                .build();
        this.create(demandingPincode, user_id);
    }
}
