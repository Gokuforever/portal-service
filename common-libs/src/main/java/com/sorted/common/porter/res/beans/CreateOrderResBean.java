package com.sorted.common.porter.res.beans;

import com.sorted.common.porter.res.beans.FetchOrderRes.FareDetails.FareAmountDetails;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CreateOrderResBean {

	private String request_id;
	private String order_id;
	private LocalDateTime estimated_pickup_time;
	private FareAmountDetails estimated_fare_details;
	private String tracking_url;

}
