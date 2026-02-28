package com.sorted.common.entity.service;

import com.sorted.common.beans.BusinessHours;
import com.sorted.common.entity.mongo.Seller;
import com.sorted.common.enums.WeekDay;
import com.sorted.common.repository.mongo.Seller_Repository;
import com.sorted.common.utils.SequenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class Seller_Service extends GenericEntityServiceImpl<String, Seller, Seller_Repository> {

    @Autowired
    private SequenceService sequenceService;

    @Override
    protected Class<Seller_Repository> getRepoClass() {
        return Seller_Repository.class;
    }

    @Override
    protected void validateBeforeCreate(Seller inE) throws RuntimeException {
        if (inE.getBusiness_hours() == null) {
            inE.setBusiness_hours(BusinessHours.builder().start_time(10).end_time(7).fixed_off_days(List.of(WeekDay.SUNDAY)).build());
        }
        inE.setStore_no(sequenceService.generateIdWithoutRandomString("SPS"));
    }

    @Override
    protected void validateBeforeUpdate(String id, Seller inE) throws RuntimeException {
    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {
    }

}
