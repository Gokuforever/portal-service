package com.sorted.common.entity.mongo;

import com.sorted.common.beans.Media;
import com.sorted.common.beans.SelectedSubCategories;
import com.sorted.common.entity.beans.ProductReview;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "products")
public class Products extends BaseMongoEntity<String> {
	/**
	* 
	*/
	@Serial
	private static final long serialVersionUID = 1L;
	private String product_code;
	private String name;
	private Long cost_price;
	private Long selling_price;
	private Long mrp;
	private String discount_tag;
	private String description;
	private Long quantity;
	private String category_id;
	private String category_code;
	private String seller_id;
	private String seller_code;
	private String varient_mapping_id;
	private Integer group_id;
	private Boolean is_secure;
	private List<SelectedSubCategories> selected_sub_catagories;
	private List<Media> media;
	private String product_master_id;
    private List<ProductReview> reviews;
}
