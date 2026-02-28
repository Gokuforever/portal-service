package com.sorted.common.entity.mongo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "sms_pool")
public class SmsPool extends BaseMongoEntity<String> {
	/**
	* 
	*/
	private static final long serialVersionUID = 1L;
	private String mobile_no;
	private String content;
	private String raw_response;
	private String status;
	private String response_id;
	private boolean is_sent;

}
