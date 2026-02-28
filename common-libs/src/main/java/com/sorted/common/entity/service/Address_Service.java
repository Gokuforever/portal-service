package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.Address;
import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.helper.AggregationFilter;
import com.sorted.common.repository.mongo.Address_Repository;
import com.sorted.common.utils.CommonUtils;
import lombok.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Service
public class Address_Service extends GenericEntityServiceImpl<String, Address, Address_Repository> {

    @Override
    protected Class<Address_Repository> getRepoClass() {
        return Address_Repository.class;
    }

    @Override
    protected void validateBeforeCreate(Address inE) throws RuntimeException {
        String code = CommonUtils.createCode("ADD");
        inE.setCode(code);
    }

    @Override
    protected void validateBeforeUpdate(String id, Address inE) throws RuntimeException {
    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {
    }

    public void markDefault(@NonNull Address address, String cudBy) {
        AggregationFilter.SEFilter filterA = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filterA.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterA.addClause(AggregationFilter.WhereClause.notEq(BaseMongoEntity.Fields.id, address.getId()));
        filterA.addClause(AggregationFilter.WhereClause.eq(Address.Fields.entity_id, address.getEntity_id()));
        List<Address> addresses = this.repoFind(filterA);
        if (!CollectionUtils.isEmpty(addresses)) {
            List<Address> list = addresses.stream().filter(x -> Boolean.TRUE.equals(x.getIs_default())).toList();
            list.forEach(add -> {
                add.setIs_default(false);
                this.update(add.getId(), add, add.getModified_by());
            });
        }
        address.setIs_default(true);
        this.update(address.getId(), address, cudBy);
    }

}
