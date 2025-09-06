package com.sorted.portal.service;

import com.sorted.commons.beans.NearestSellerRes;
import com.sorted.commons.entity.mongo.Address;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Pincode_Master;
import com.sorted.commons.entity.mongo.Seller;
import com.sorted.commons.entity.service.*;
import com.sorted.commons.enums.All_Status;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter;
import com.sorted.commons.porter.req.beans.GetQuoteRequest;
import com.sorted.commons.porter.res.beans.GetQuoteResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.PorterUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NearestSellerService {

    private final Pincode_Master_Service pincode_Master_Service;
    private final Address_Service address_Service;
    private final Seller_Service seller_Service;
    private final StoreActivityService storeActivityService;
    private final DemandingPincodeService demandingPincodeService;
    private final PorterUtility porterUtility;

    @Value("${se.porter.store.operational.check.enabled:false}")
    private boolean porterStoreOperationalCheckEnabled;

    @Value("${porter.country.code}")
    private String countryCode;


    public NearestSellerRes getNearestSeller(String pincode, String mobile_no, String user_name, String user_id) {

        AggregationFilter.SEFilter filterP = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filterP.addClause(AggregationFilter.WhereClause.eq(Pincode_Master.Fields.pincode, pincode));
        filterP.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Pincode_Master pincode_Master = pincode_Master_Service.repoFindOne(filterP);
        if (pincode_Master == null) {
            demandingPincodeService.storeDemandingPincode(pincode, user_id);
            throw new CustomIllegalArgumentsException(ResponseCode.NOT_DELIVERIBLE);
        }

        AggregationFilter.SEFilter filterS = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filterS.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterS.addClause(AggregationFilter.WhereClause.eq(Seller.Fields.status, All_Status.Seller_Status.ACTIVE.name()));

        Map<String, String> map;
        boolean isStoreOperational = true;
        List<Seller> listS = seller_Service.repoFind(filterS);
        if (CollectionUtils.isEmpty(listS)) {
            throw new CustomIllegalArgumentsException(ResponseCode.NOT_DELIVERIBLE);
        }
        List<String> operationalStores = storeActivityService.getOperationalStores(listS.stream().map(Seller::getId).toList());
        if (operationalStores.isEmpty()) {
            isStoreOperational = false;
            operationalStores.addAll(listS.stream().map(Seller::getId).toList());
        }
        if (porterStoreOperationalCheckEnabled) {
            map = listS.stream().filter(e -> operationalStores.contains(e.getId()) && StringUtils.hasText(e.getAddress_id())).collect(Collectors.toMap(Seller::getAddress_id, BaseMongoEntity::getId));
        } else {
            map = listS.stream().filter(e -> StringUtils.hasText(e.getAddress_id())).collect(Collectors.toMap(Seller::getAddress_id, BaseMongoEntity::getId));
        }
        AggregationFilter.SEFilter filterA = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filterA.addClause(AggregationFilter.WhereClause.in(BaseMongoEntity.Fields.id, CommonUtils.convertS2L(map.keySet())));
        filterA.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Address> listAdd = address_Service.repoFind(filterA);

        Map<String, Address> mapA = listAdd.stream().collect(Collectors.toMap(BaseMongoEntity::getId, e -> e));

        String nearestSeller = CommonUtils.findNearestSeller(pincode_Master.getLatitude(), pincode_Master.getLongitude(), listAdd);

        Address address = mapA.get(nearestSeller);

        // @formatter:off
        GetQuoteRequest quoteRequest = GetQuoteRequest.builder()
                .pickup_details(GetQuoteRequest.PickupDetails.builder()
                        .lat(address.getLat().doubleValue())
                        .lng(address.getLng().doubleValue())
                        .build())
                .drop_details(GetQuoteRequest.DropDetails.builder()
                        .lat(pincode_Master.getLatitude())
                        .lng(pincode_Master.getLongitude())
                        .build())
                .customer(GetQuoteRequest.Customer.builder()
                        .name(StringUtils.hasText(user_name) ? user_name : "Studeaze")
                        .mobile(GetQuoteRequest.Customer.Mobile.builder()
                                .country_code(countryCode)
                                .number(StringUtils.hasText(mobile_no) ? mobile_no : "9867292392")
                                .build())
                        .build())
                .build();
        // @formatter:on
        GetQuoteResponse getQuoteResponse = porterUtility.getDeliveryQuote(quoteRequest);
        return NearestSellerRes.builder().response(getQuoteResponse).seller_id(address.getEntity_id()).is_operational(isStoreOperational).build();
    }
}
