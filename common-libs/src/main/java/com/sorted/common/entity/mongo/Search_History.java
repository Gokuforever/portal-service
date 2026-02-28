package com.sorted.common.entity.mongo;

import com.sorted.common.helper.AggregationFilter.SEFilter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants
@Document(collection = "search_history")
public class Search_History extends BaseMongoEntity<String> {
	/**
	* 
	*/
	private static final long serialVersionUID = 1L;
	private String user_id;
	private Integer user_type_id;
	private SEFilter filter;

}
