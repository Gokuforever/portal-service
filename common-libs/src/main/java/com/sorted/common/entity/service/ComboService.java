package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.Combo;
import com.sorted.common.repository.mongo.ComboRepository;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.UUID;

@Service
public class ComboService extends GenericEntityServiceImpl<String, Combo, ComboRepository> {
    @Override
    protected Class<ComboRepository> getRepoClass() {
        return ComboRepository.class;
    }

    @Override
    protected void validateBeforeCreate(Combo inE) throws RuntimeException {
        long nanoseconds = System.nanoTime();
        String productCode = "CBID-" + nanoseconds + "-" + Year.now() + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        inE.setCode(productCode);
    }

    @Override
    protected void validateBeforeUpdate(String id, Combo inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }
}
