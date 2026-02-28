package com.sorted.common.entity.mongo;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Builder
@Document(collection = "launching_email")
public class LaunchingEmail extends BaseMongoEntity<String> {
    private String mail;
    private boolean sent;
}
