package com.sorted.common.entity.mongo;

import com.sorted.common.enums.LogType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Builder
@Document(collection = "javascript_log_trace")
public class JavascriptLogTrace extends BaseMongoEntity<String> {

    private Object data;

    @Field("user_id")
    private String userId;

    @Field("log_type")
    private LogType logType;
}
