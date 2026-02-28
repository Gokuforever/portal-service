package com.sorted.common.entity.mongo;

import com.sorted.common.beans.EducationCategoryField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "education_categories")
public class EducationCategories extends BaseMongoEntity<String> {
    private String education_level;
    private List<EducationCategoryField> fields;
    private List<String> category_ids;
}

