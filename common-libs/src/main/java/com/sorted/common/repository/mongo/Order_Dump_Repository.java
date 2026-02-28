package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Order_Dump;
import com.sorted.common.helper.BaseMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Order_Dump_Repository extends BaseMongoRepository<String, Order_Dump> {

    @Override
    default Class<Order_Dump> getEntityType() {
        return Order_Dump.class;
    }
}
