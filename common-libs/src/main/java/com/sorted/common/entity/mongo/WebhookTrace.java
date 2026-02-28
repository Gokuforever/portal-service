package com.sorted.common.entity.mongo;

import com.sorted.common.enums.WebhookType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "webhook_trace")
@Builder
public class WebhookTrace extends BaseMongoEntity<String> {

    private Object request;
    private Object response;
    private String order_id;
    private WebhookType type;
}
