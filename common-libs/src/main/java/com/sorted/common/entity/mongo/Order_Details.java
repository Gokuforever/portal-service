package com.sorted.common.entity.mongo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sorted.common.beans.AddressDTO;
import com.sorted.common.beans.DeliveryRequestAttempts;
import com.sorted.common.beans.Order_Status_History;
import com.sorted.common.beans.SettlementDetails;
import com.sorted.common.enums.OrderStatus;
import com.sorted.common.enums.TimeSlot;
import com.sorted.common.porter.res.beans.FetchOrderRes.FareDetails;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.util.CollectionUtils;

import java.io.Serial;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "order_details")
public class Order_Details extends BaseMongoEntity<String> {
    /**
     *
     */
    @Serial
    private static final long serialVersionUID = 1L;
    private String code;
    private String user_id;
    private String payment_mode;
    private String pg_order_id;
    private Long delivery_charges;
    private Long handling_charges;
    private Long small_cart_fee;
    private Long total_discount;
    private Long total_items_cost;
    private Long total_amount;
    private OrderStatus status;
    private Integer status_id;
    private String transaction_id;
    private List<Order_Status_History> order_status_history;
    private AddressDTO delivery_address;
    private AddressDTO pickup_address;
    private String payment_status;
    private String shipment_status;
    private LocalDateTime expected_delivery_date;
    private Long actual_delivery_charges;
    private Long estimated_delivery_charges;
    private Long seller_share;
    private Boolean is_payout_done;
    private Long studeaze_share;
    private List<DeliveryRequestAttempts> delivery_request_attempts;
    private String dp_order_id; // delivery partner order id
    private LocalDateTime estimated_pickup_time;
    private FareDetails fare_details;
    private String estimated_quote;
    private String seller_id;
    @Version
    private Long version;
    private SettlementDetails settlement_details;
    private String rejection_reason;
    private String refund_transaction_id;
    private TimeSlot secured_time_slot;
    private LocalDate secured_date;
    private int max_secured_reschedule_count;
    private AddressDTO secure_pickup_address;
    private AddressDTO secure_delivery_address;
    private String secure_dp_order_id;
    private String secure_order_id;
    private String secure_return_failure_reason;
    private boolean secure_return_initiated;
    private String coupon_code;
    private Integer refund_retry_count;
    private LocalDateTime last_refund_retry_date;
    private String refund_failure_reason;
    private Long partial_refund_amount;
    private String partial_refund_transaction_id;



    @JsonIgnore
    public void setStatus(@NonNull OrderStatus status, String cud_by) {
        Order_Status_History order_Status_History = Order_Status_History.builder().status(status)
                .modification_date(LocalDateTime.now()).modified_by(cud_by).build();
        List<Order_Status_History> list = CollectionUtils.isEmpty(getOrder_status_history()) ? new ArrayList<>()
                : getOrder_status_history();
        list.add(order_Status_History);
        setOrder_status_history(list);
        this.status = status;
        this.status_id = status.getId();
    }

    private void setStatus(OrderStatus status) {
    }

    private void setStatus_id(Integer status_id) {
    }

    private void setStatus_id(int status_id) {
    }

    @JsonIgnore
    public void setFare_details(FareDetails fare_details) {
        if (compareHash(fare_details)) {
            this.fare_details = fare_details;
        }
    }

    @JsonIgnore
    private boolean compareHash(FareDetails details) {
        int hash = Objects.hash(fare_details);
        int hash2 = Objects.hash(details);
        return hash != hash2;
    }

    @JsonIgnore
    public FareDetails getFare_details() {
        if (fare_details == null) {
            fare_details = FareDetails.builder().build();
        }
        return fare_details;
    }

}