package com.sorted.common.entity.mongo;

import com.sorted.common.enums.ThirdPartyAPIType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Builder
@Document(collection = "third_party_api_trace")
public class ThirdPartyAPITraceEntity extends BaseMongoEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;
    @Field("request_type")
    private ThirdPartyAPIType requestType;
    @Field("raw_request")
    private String rawRequest;
    @Field("raw_response")
    private String rawResponse;
    @Field("error_message")
    private String errorMessage;
}
