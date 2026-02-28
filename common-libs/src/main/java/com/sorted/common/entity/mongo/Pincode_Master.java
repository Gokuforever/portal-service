package com.sorted.common.entity.mongo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants
@Document(collection = "pincode_master")
public class Pincode_Master extends BaseMongoEntity<String> {
	/**
	* 
	*/
	private static final long serialVersionUID = 1L;
	private String circlename;
	private String regionname;
	private String divisionname;
	private String district;
	private String statename;
	private String pincode;
	private double latitude;
	private double longitude;
}
