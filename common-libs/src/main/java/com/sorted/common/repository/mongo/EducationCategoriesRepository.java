package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.EducationCategories;
import com.sorted.common.helper.BaseMongoRepository;

public interface EducationCategoriesRepository extends BaseMongoRepository<String, EducationCategories> {

    @Override
    default Class<EducationCategories> getEntityType() {
        return EducationCategories.class;
    }
}
