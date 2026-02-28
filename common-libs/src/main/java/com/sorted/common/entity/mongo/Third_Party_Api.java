package com.sorted.common.entity.mongo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.http.HttpStatus;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "third_party_api")
public class Third_Party_Api extends BaseMongoEntity<String> {/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String request_type;
	private String raw_request;
	private String raw_response;
	private HttpStatus status;
	
	

}
