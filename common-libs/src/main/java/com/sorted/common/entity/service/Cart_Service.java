package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.Cart;
import com.sorted.common.helper.AggregationFilter;
import com.sorted.common.repository.mongo.Cart_Repository;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class Cart_Service extends GenericEntityServiceImpl<String, Cart, Cart_Repository> {

    @Override
    protected Class<Cart_Repository> getRepoClass() {
        return Cart_Repository.class;
    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }

    @Override
    protected void validateBeforeCreate(Cart inE) throws RuntimeException {
        if (CollectionUtils.isEmpty(inE.getCart_items())) {
            inE.setCart_items(new ArrayList<>());
        }
    }

    @Override
    protected void validateBeforeUpdate(String id, Cart inE) throws RuntimeException {

    }

    public Cart findByUserId(String userId) {
        AggregationFilter.SEFilter filterC = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filterC.addClause(AggregationFilter.WhereClause.eq(Cart.Fields.user_id, userId));
        filterC.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        return this.repoFindOne(filterC);
    }

}
