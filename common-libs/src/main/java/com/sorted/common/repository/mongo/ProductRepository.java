package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Products;
import com.sorted.common.helper.AggregationFilter;
import com.sorted.common.helper.BaseMongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends BaseMongoRepository<String, Products> {

    @Override
    default Class<Products> getEntityType() {
        return Products.class;
    }

    default List<Products> getRandomProducts(AggregationFilter.SEFilter f, long count) {
        return this.random(f, count);
    }

}
