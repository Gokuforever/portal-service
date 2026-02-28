package com.sorted.common.beans;

import com.sorted.common.entity.mongo.Category_Master.SubCategory;
import lombok.Data;

import java.util.List;

@Data
public class CategoryReqBean {

	private String category_name;
	private List<SubCategory> sub_categories;

}
