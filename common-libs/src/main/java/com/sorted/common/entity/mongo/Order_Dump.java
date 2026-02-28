package com.sorted.common.entity.mongo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants
@Document(collection = "order_dump")
public class Order_Dump extends BaseMongoEntity<String> {

    public Order_Dump(String raw_request) {
        this.raw_request = raw_request;
    }

    public void markFailed(String error_message, String response_code) {
        this.status = "FAILED";
        this.error_message = error_message;
        this.response_code = response_code;
    }

    public void markSuccess(String order_id) {
        this.status = "SUCCESS";
        this.order_id = order_id;
    }


    private final String raw_request;
    private String status;
    private String error_message;
    private String response_code;
    private String order_id;
}
