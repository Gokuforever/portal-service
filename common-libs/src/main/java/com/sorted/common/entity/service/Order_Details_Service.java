package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.Order_Details;
import com.sorted.common.entity.mongo.Order_Item;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import com.sorted.common.enums.OrderStatus;
import com.sorted.common.repository.mongo.Order_Details_Repository;
import com.sorted.common.utils.SequenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class Order_Details_Service extends GenericEntityServiceImpl<String, Order_Details, Order_Details_Repository> {

    @Autowired
    private Order_Item_Service orderItemService;

    @Autowired
    private SequenceService sequenceService;

    @Override
    protected Class<Order_Details_Repository> getRepoClass() {
        return Order_Details_Repository.class;
    }

    @Override
    protected void validateBeforeCreate(Order_Details inE) throws RuntimeException {
        inE.setCode(sequenceService.generateId("ORD"));
    }

    @Override
    protected void validateBeforeUpdate(String id, Order_Details inE) throws RuntimeException {
        updateOrderItemStatus(id, inE);

    }

    private void updateOrderItemStatus(String id, Order_Details inE) {
        // Skip automatic item status sync for partial accept - items are updated individually
        if (isPartialAcceptStatus(inE.getStatus())) {
            return;
        }

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(Order_Item.Fields.order_id, id));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<Order_Item> orderItems = orderItemService.repoFind(filter);
        orderItems.forEach(orderItem -> {
            // Skip items that have been individually rejected or refunded
            if (isItemLevelStatus(orderItem.getStatus())) {
                return;
            }
            if (!inE.getStatus().equals(orderItem.getStatus())) {
                orderItem.setStatus(inE.getStatus(), inE.getOrder_status_history().get(inE.getOrder_status_history().size() - 1).getModified_by());
                orderItemService.update(orderItem.getId(), orderItem, inE.getOrder_status_history().get(inE.getOrder_status_history().size() - 1).getModified_by());
            }
        });
    }

    /**
     * Check if the order status is related to partial acceptance
     */
    private boolean isPartialAcceptStatus(OrderStatus status) {
        return status == OrderStatus.PARTIALLY_ACCEPTED;
    }

    /**
     * Check if the item has an item-level status that should not be overwritten
     */
    private boolean isItemLevelStatus(OrderStatus status) {
        return Arrays.asList(
                OrderStatus.ITEM_REJECTED,
                OrderStatus.ITEM_REFUND_INITIATED
        ).contains(status);
    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {
    }
}
