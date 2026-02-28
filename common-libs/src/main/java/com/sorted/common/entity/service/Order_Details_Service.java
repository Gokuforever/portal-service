package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.Order_Details;
import com.sorted.common.entity.mongo.Order_Item;
import com.sorted.common.enums.OrderStatus;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import com.sorted.common.repository.mongo.Order_Details_Repository;
import com.sorted.common.utils.SequenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class Order_Details_Service extends GenericEntityServiceImpl<String, Order_Details, Order_Details_Repository> {

    private final List<OrderStatus> secureStatus = List.of(OrderStatus.SECURE_RETURN_INITIATED, OrderStatus.SECURE_RETURN_COMPLETED, OrderStatus.SECURE_RETURN_SCHEDULED,
            OrderStatus.SECURE_RETURN_FAILED, OrderStatus.ITEMS_PICKED_UP_FOR_SECURE_RETURN, OrderStatus.ORDER_CANCELLED_FOR_SECURE_RETURN, OrderStatus.RIDER_ASSIGNED_FOR_SECURE_RETURN);

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
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(Order_Item.Fields.order_id, id));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<Order_Item> orderItems = orderItemService.repoFind(filter);
        orderItems.forEach(orderItem -> {
            if (!secureStatus.contains(inE.getStatus()) && !inE.getStatus().equals(orderItem.getStatus())) {
                orderItem.setStatus(inE.getStatus(), inE.getOrder_status_history().get(inE.getOrder_status_history().size() - 1).getModified_by());
                orderItemService.update(orderItem.getId(), orderItem, inE.getOrder_status_history().get(inE.getOrder_status_history().size() - 1).getModified_by());
            }
        });
    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {
    }

    public void updateForSecureItems(String id, Order_Details inE, String cudby, List<String> secureItemIds) {
        super.update(id, inE, cudby);

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(Order_Item.Fields.order_id, id));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter.addClause(WhereClause.in(BaseMongoEntity.Fields.id, secureItemIds));
        List<Order_Item> orderItems = orderItemService.repoFind(filter);
        orderItems.forEach(orderItem -> {
            if (!secureStatus.contains(inE.getStatus()) && !inE.getStatus().equals(orderItem.getStatus())) {
                orderItem.setStatus(inE.getStatus(), inE.getOrder_status_history().get(inE.getOrder_status_history().size() - 1).getModified_by());
                orderItemService.update(orderItem.getId(), orderItem, inE.getOrder_status_history().get(inE.getOrder_status_history().size() - 1).getModified_by());
            }
        });
    }
}
