package com.sorted.common.entity.mongo;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "feedback")
@Builder
public class Feedback extends BaseMongoEntity<String> {

    @Field("user_id")
    private String userId;
    private String feedback;
}
