package com.sorted.common.beans;

import com.sorted.common.entity.mongo.Category_Master.SubCategory;
import lombok.Data;

import java.util.List;

@Data
public class ProductMISReqBean {

	private String name;
	private String base_category_code;
	private List<SubCategory> sub_categories;

}
