package com.sorted.common.entity.mongo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "product_master_new")
public class Product_Master extends BaseMongoEntity<String> {
	/**
	* 
	*/
	@Serial
	private static final long serialVersionUID = 1L;

	private String catagory_id;
	private String name;
	private String img_src;
	private Integer group_id;
	private String group_name;
	private Map<String, List<String>> sub_categories;
	private String cdn_url;
	private Long mrp;
	private String desc;
}
