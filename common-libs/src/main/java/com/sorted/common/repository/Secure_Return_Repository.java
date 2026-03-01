package com.sorted.common.repository;

import com.sorted.common.entity.mongo.Secure_Return;
import com.sorted.common.helper.BaseMongoRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Secure_Return entity.
 * Provides data access methods for secure buy/return operations.
 */
@Repository
public interface Secure_Return_Repository extends BaseMongoRepository<String, Secure_Return> {

    @Override
    default Class<Secure_Return> getEntityType() {
        return Secure_Return.class;
    }
}
