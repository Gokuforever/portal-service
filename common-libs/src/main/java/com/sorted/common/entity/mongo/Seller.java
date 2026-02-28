package com.sorted.common.entity.mongo;

import com.sorted.common.beans.Bank_Details;
import com.sorted.common.beans.BusinessHours;
import com.sorted.common.beans.Spoc_Details;
import com.sorted.common.enums.All_Status.Seller_Status;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "seller")
public class Seller extends BaseMongoEntity<String> {

    @Serial
    private static final long serialVersionUID = 7105004513020596023L;

    private String code;
    private String business_name;
    private String store_no;
    //	private AddressDTO business_address;
    private List<Spoc_Details> spoc_details;
    private List<String> serviceable_pincodes;
    private String company_pan;
    private Bank_Details bank_details;
    private Seller_Status status = Seller_Status.VERIFICATION_PENDING;
    private String address_id;
    // Not In Use
    private String cin;
    private String gstin;
    private String business_type;
    private BigDecimal fee_in_percentage;
    private BusinessHours business_hours;
    private List<String> deliverableZones;

    public void setDeliverableZones(String... zoneId) {
        if (deliverableZones == null) {
            deliverableZones = new ArrayList<>();
        }
        List<String> list = Arrays.asList(zoneId);
        list.addAll(deliverableZones);
        list = list.stream().distinct().toList();
        deliverableZones.clear();
        deliverableZones.addAll(list);
    }

}
