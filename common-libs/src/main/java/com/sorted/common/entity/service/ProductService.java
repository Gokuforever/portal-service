package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.Products;
import com.sorted.common.repository.mongo.ProductRepository;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.UUID;

@Service
public class ProductService extends GenericEntityServiceImpl<String, Products, ProductRepository> {

    @Override
    protected Class<ProductRepository> getRepoClass() {
        return ProductRepository.class;
    }

    @Override
    protected void validateBeforeCreate(Products inE) throws RuntimeException {
        long nanoseconds = System.nanoTime();
        String productCode = "PID-" + nanoseconds + "-" + Year.now() + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        inE.setProduct_code(productCode);
    }


    @Override
    protected void validateBeforeUpdate(String id, Products inE) throws RuntimeException {
    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }

}
