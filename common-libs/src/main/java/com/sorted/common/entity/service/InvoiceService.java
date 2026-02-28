package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.Invoice;
import com.sorted.common.repository.mongo.InvoiceRepository;
import com.sorted.common.utils.SequenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InvoiceService extends GenericEntityServiceImpl<String, Invoice, InvoiceRepository> {

    @Autowired
    private SequenceService sequenceService;

    @Override
    protected Class<InvoiceRepository> getRepoClass() {
        return InvoiceRepository.class;
    }

    @Override
    protected void validateBeforeCreate(Invoice inE) throws RuntimeException {
        inE.setInvoiceId(sequenceService.generateId("INV"));
    }

    @Override
    protected void validateBeforeUpdate(String id, Invoice inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }
}
