package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.Order_Details;
import com.sorted.common.entity.mongo.Order_Item;
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
            if (!inE.getStatus().equals(orderItem.getStatus())) {
                orderItem.setStatus(inE.getStatus(), inE.getOrder_status_history().get(inE.getOrder_status_history().size() - 1).getModified_by());
                orderItemService.update(orderItem.getId(), orderItem, inE.getOrder_status_history().get(inE.getOrder_status_history().size() - 1).getModified_by());
            }
        });
    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {
    }
}
