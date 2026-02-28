package com.sorted.common.porter.req.beans;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PorterWebhookBean {

    @JsonProperty("status")
    private String status;

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("order_details")
    private OrderDetails orderDetails;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OrderDetails {
        @JsonProperty("event_ts")
        private Long eventTs;

        @JsonProperty("partner_location")
        private PartnerLocation partnerLocation;

        @JsonProperty("driver_details")
        private DriverDetails driverDetails;

        @JsonProperty("estimated_trip_fare")
        private Long estimatedTripFare;

        @JsonProperty("actual_trip_fare")
        private Long actualTripFare;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PartnerLocation {
        @JsonProperty("lat")
        private Double lat;

        @JsonProperty("long")
        private Double lon;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DriverDetails {
        @JsonProperty("driver_name")
        private String driverName;

        @JsonProperty("vehicle_number")
        private String vehicleNumber;

        @JsonProperty("mobile")
        private String mobile;
    }

}
