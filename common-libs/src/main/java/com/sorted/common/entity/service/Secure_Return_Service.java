package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.Secure_Return;
import com.sorted.common.enums.SecureReturnStatus;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import com.sorted.common.repository.Secure_Return_Repository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service layer for Secure_Return entity.
 * Provides CRUD operations and business logic for secure returns.
 */

@Slf4j
@Service
public class Secure_Return_Service extends GenericEntityServiceImpl<String, Secure_Return, Secure_Return_Repository> {

    @Value("${se.common.secure_pickup.mock.enabled:false}")
    private boolean mockEnabled;

    @Override
    protected Class<Secure_Return_Repository> getRepoClass() {
        return Secure_Return_Repository.class;
    }

    @Override
    protected void validateBeforeCreate(Secure_Return inE) throws RuntimeException {
        // Validation logic can be added here if needed
        log.debug("Validating secure return before create for order: {}", inE.getOrder_id());
    }

    @Override
    protected void validateBeforeUpdate(String id, Secure_Return inE) throws RuntimeException {
        // Validation logic can be added here if needed
        log.debug("Validating secure return before update: {}", id);
    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {
        // Validation logic can be added here if needed
        log.debug("Validating secure return before delete: {}", id);
    }

    public Secure_Return findByOrderId(String orderId) {
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(Secure_Return.Fields.order_id, orderId));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        return this.repoFind(filter).stream().findFirst().orElse(null);
    }

    public List<Secure_Return> fetchScheduledSecureReturns() {

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(Secure_Return.Fields.status_id, SecureReturnStatus.SCHEDULED.getId()));
        if (!mockEnabled) {
            filter.addClause(WhereClause.lte(Secure_Return.Fields.scheduled_pickup_date, LocalDateTime.now()));
        }
//        filter.addClause(WhereClause.eq(Secure_Return.Fields.scheduled_time_slot, timeSlot.name()));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        return this.repoFind(filter);
    }

    public List<Secure_Return> fetchInTransitOrders() {
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.in(Secure_Return.Fields.status_id, List.of(SecureReturnStatus.IN_TRANSIT.getId(), SecureReturnStatus.PICKUP_PENDING.getId(),
                SecureReturnStatus.PICKUP_ASSIGNED.getId(),
                SecureReturnStatus.IN_TRANSIT.getId())));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        return this.repoFind(filter);
    }

    // Additional custom query methods can be added here if needed
    // The base class GenericEntityServiceImpl provides:
    // - create(entity, cudby)
    // - update(id, entity, cudby)
    // - repoFind(filter)
    // - repoFindOne(filter)
    // - repoFindAll()
    // - countByFilter(filter)
    // - bulkCreate(entities, cudby)
}
