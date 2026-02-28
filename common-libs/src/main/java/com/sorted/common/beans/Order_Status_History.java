package com.sorted.common.beans;

import com.sorted.common.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@FieldNameConstants
public class Order_Status_History implements Serializable {
	/**
	 * 
	 */
	@Serial
	private static final long serialVersionUID = 1L;
	private OrderStatus status;
	private LocalDateTime modification_date;
	private String modified_by;
}
