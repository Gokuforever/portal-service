package com.sorted.common.entity.mongo;

import com.sorted.common.beans.Item;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "cart")
public class Cart extends BaseMongoEntity<String> {
	/**
	* 
	*/
	@Serial
	private static final long serialVersionUID = 1L;
	private String user_id;
	private List<Item> cart_items;
	private BigDecimal total_price;
	private Long delivery_charges;
	private Long handling_charges;
	private Long small_cart_fee;
	@Field("coupon_code")
	private String couponCode;
}
