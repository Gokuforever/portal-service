package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Invoice;
import com.sorted.common.helper.BaseMongoRepository;

public interface InvoiceRepository extends BaseMongoRepository<String, Invoice> {
    @Override
    default Class<Invoice> getEntityType() {
        return Invoice.class;
    }
}
