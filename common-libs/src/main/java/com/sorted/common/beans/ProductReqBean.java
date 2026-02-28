package com.sorted.common.beans;

import com.sorted.common.entity.mongo.Category_Master;
import com.sorted.common.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
public class ProductReqBean extends ReqBaseBean {
	private String product_master_id;
	private String product_id;
	private String seller_id;
	private String category_id;
	private Map<String, List<String>> sub_categories;
	private Integer group_id;
	private String name;
	private String selling_price;
	private String mrp;
	private String description;
	private String quantity;
	private Category_Master selected_category;
	private List<Media> media;
	private String varient_name;
	private String varient_mapping_id;
	private Boolean is_secure;

}
