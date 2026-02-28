package com.sorted.common.entity.mongo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "store_activity")
public class StoreActivity extends BaseMongoEntity<String> {
    private String store_id;
    private Integer activity_id;

}
