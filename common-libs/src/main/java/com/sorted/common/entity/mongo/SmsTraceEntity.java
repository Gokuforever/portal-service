package com.sorted.common.entity.mongo;

import com.sorted.common.enums.SmsTemplate;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "sms_trace")
@Builder
public class SmsTraceEntity extends BaseMongoEntity<String> {

    private String mobile;
    private String content;
    @Field("raw_response")
    private String rawResponse;
    private String status;
    @Field("response_id")
    private String responseId;
    @Field("is_sent")
    private boolean isSent;
    private SmsTemplate template;
    @Field("error_desc")
    private String errorDesc;

}
