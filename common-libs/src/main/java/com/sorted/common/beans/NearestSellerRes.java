package com.sorted.common.beans;

import com.sorted.common.porter.res.beans.GetQuoteResponse;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
public class NearestSellerRes implements Serializable{

	/**
	 * 
	 */
	@Serial
	private static final long serialVersionUID = 1L;
	private String seller_id;
	private GetQuoteResponse response;
	private boolean is_operational;

}
