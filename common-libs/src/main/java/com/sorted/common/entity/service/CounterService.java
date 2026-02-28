package com.sorted.common.entity.service;

import com.sorted.common.constants.Defaults;
import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.Counter;
import com.sorted.common.helper.AggregationFilter.*;
import com.sorted.common.repository.mongo.CounterRepository;
import org.springframework.stereotype.Service;

@Service
public class CounterService extends GenericEntityServiceImpl<String, Counter, CounterRepository> {
    @Override
    protected Class<CounterRepository> getRepoClass() {
        return CounterRepository.class;
    }

    @Override
    protected void validateBeforeCreate(Counter inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeUpdate(String id, Counter inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }

    public long getNextSequence(String name) {
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(Counter.Fields.prefix, name));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        OrderBy orderBy = new OrderBy(Counter.Fields.counter, SortOrder.DESC);
        filter.setOrderBy(orderBy);

        Counter counter = this.repoFindOne(filter);
        if (counter == null) {
            counter = Counter.builder()
                    .prefix(name)
                    .counter(1L)
                    .build();
            this.create(counter, Defaults.SYSTEM_ADMIN);
        } else {
            counter.setCounter(counter.getCounter() + 1);
            this.update(counter.getId(), counter, Defaults.SYSTEM_ADMIN);
        }
        return counter.getCounter();
    }
}
