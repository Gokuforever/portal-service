package com.sorted.common.beans;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

import java.time.LocalDateTime;

@Data
@FieldNameConstants
public class Return_Details {
	private LocalDateTime return_date;
	private String return_reason;
	private Long refund_amount;
	private String return_status;
}
