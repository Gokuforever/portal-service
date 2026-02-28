package com.sorted.common.beans;

import com.sorted.common.enums.ProductType;
import com.sorted.common.enums.PurchaseType;
import lombok.Data;

@Data
public class Item {
	private String product_id;
	private String product_code;
	private Long quantity;
	private PurchaseType purchase_type;
	private boolean is_secure;
	private ProductType product_type;
	private boolean combo;
}
