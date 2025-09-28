package com.sorted.portal.bl_services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sorted.commons.beans.AddressDTO;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.service.*;
import com.sorted.commons.enums.*;
import com.sorted.commons.exceptions.BaseException;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.porter.req.beans.GetQuoteRequest;
import com.sorted.commons.porter.res.beans.GetQuoteResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.PorterUtility;
import com.sorted.commons.utils.ValidationUtil;
import com.sorted.portal.request.beans.AddressBean;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Slf4j
@RequestMapping("/address")
@RestController
@RequiredArgsConstructor
public class ManageAddress_BLService {

    private final Users_Service users_Service;
    private final RoleService roleService;
    private final Address_Service address_Service;
    private final Pincode_Master_Service pincode_Master_Service;
    private final DemandingPincodeService demandingPincodeService;
    private final PorterUtility porterUtility;
    private final Seller_Service seller_Service;

    @PostMapping("/add")
    public SEResponse add(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            return createOrUpdateAddress(request, httpServletRequest, false);
        } catch (BaseException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("add/address:: exception occurred");
            log.error("add/address:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/update")
    public SEResponse update(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            return createOrUpdateAddress(request, httpServletRequest, true);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("address/update:: exception occurred");
            log.error("address/update:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    private SEResponse createOrUpdateAddress(SERequest request, HttpServletRequest httpServletRequest, boolean isEdit) throws JsonProcessingException {
        log.info("createOrUpdateAddress:: method started");
        log.info("createOrUpdateAddress:: request: {}, isEdit: {}", request, isEdit);
        AddressBean req = request.getGenericRequestDataObject(AddressBean.class);
        CommonUtils.extractHeaders(httpServletRequest, req);
        UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Permission.EDIT, Activity.MANAGE_ADDRESS);
        String user_id;
        UserType user_type = usersBean.getRole().getUser_type();
        switch (user_type) {
            case CUSTOMER, GUEST:
                user_id = usersBean.getId();
                break;
            case SUPER_ADMIN:
                if (!StringUtils.hasText(req.getUser_id())) {
                    throw new CustomIllegalArgumentsException("User id not selected.");
                }
                user_id = req.getUser_id();
                SEFilter filterU = new SEFilter(SEFilterType.AND);
                filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, user_id));
                filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
                Users users = users_Service.repoFindOne(filterU);
                if (users == null) {
                    throw new CustomIllegalArgumentsException("Invalid user selected.");
                }
                SEFilter filterR = new SEFilter(SEFilterType.AND);
                filterR.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, users.getRole_id()));
                filterR.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

                Role role = roleService.repoFindOne(filterR);
                if (role == null) {
                    throw new CustomIllegalArgumentsException("Invalid user selected.");
                }
                break;
            default:
                throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
        }

        AddressDTO addressDTO = req.getAddress();
        if (addressDTO == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.MISSING_ADDRESS);
        }

        Address address;
        if (isEdit) {
            if (!StringUtils.hasText(addressDTO.getId())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_ID);
            } else {
                SEFilter filterA = new SEFilter(SEFilterType.AND);
                filterA.addClause(WhereClause.eq(Address.Fields.entity_id, user_id));
                filterA.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, addressDTO.getId()));
                filterA.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
                filterA.addClause(WhereClause.eq(Address.Fields.user_type, user_type.name()));

                address = address_Service.repoFindOne(filterA);
                if (address == null) {
                    throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
                }
            }
        } else {
            address = new Address();
        }

        ValidationUtil.validateAddress(addressDTO, address);

        SEFilter filterP = new SEFilter(SEFilterType.AND);
        filterP.addClause(WhereClause.eq(Pincode_Master.Fields.pincode, address.getPincode()));
        filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Pincode_Master pincode_Master = pincode_Master_Service.repoFindOne(filterP);
        if (pincode_Master == null) {
            demandingPincodeService.storeDemandingPincode(address.getPincode(), user_id);
            throw new CustomIllegalArgumentsException(ResponseCode.NOT_DELIVERIBLE);
        }

        SEFilter filterA = new SEFilter(SEFilterType.AND);
        filterA.addClause(WhereClause.eq(Address.Fields.entity_id, user_id));
        filterA.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterA.addClause(WhereClause.eq(Address.Fields.user_type, user_type.name()));
        if (isEdit) {
            filterA.addClause(WhereClause.notEq(BaseMongoEntity.Fields.id, addressDTO.getId()));
        }

        List<Address> listA = address_Service.repoFind(filterA);
        if (!CollectionUtils.isEmpty(listA)) {
            Predicate<Address> p1 = x -> x.getAddress_type() != AddressType.OTHER;
            Predicate<Address> p2 = x -> x.getAddress_type() == address.getAddress_type();
            boolean anyMatch = listA.stream().anyMatch(p1.and(p2));
            if (anyMatch) {
                throw new CustomIllegalArgumentsException("If you havel multiple " + address.getAddress_type().getType()
                        + " address, please use other address type");
            }
        }

        Seller seller = seller_Service.findById("68711a63a2dcdf55ed170972").orElseThrow();

        Address pickUpAddress = address_Service.findById(seller.getAddress_id()).orElseThrow();

        // @formatter:off
        GetQuoteRequest quoteRequest = GetQuoteRequest.builder()
                .pickup_details(GetQuoteRequest.PickupDetails.builder()
                        .lat(pickUpAddress.getLat().doubleValue())
                        .lng(pickUpAddress.getLng().doubleValue())
                        .build())
                .drop_details(GetQuoteRequest.DropDetails.builder()
                        .lat(address.getLat().doubleValue())
                        .lng(address.getLng().doubleValue())
                        .build())
                .customer(GetQuoteRequest.Customer.builder()
                        .name(StringUtils.hasText(usersBean.getFirst_name()) ? usersBean.getFirst_name() : "Studeaze")
                        .mobile(GetQuoteRequest.Customer.Mobile.builder()
                                .country_code("+91")
                                .number(StringUtils.hasText(usersBean.getMobile_no()) ? usersBean.getMobile_no() : "9867292392")
                                .build())
                        .build())
                .build();
        // @formatter:on
        GetQuoteResponse getQuoteResponse = porterUtility.getDeliveryQuote(quoteRequest);
        if (getQuoteResponse == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }

        address.setUser_type(user_type);
        address.setEntity_id(usersBean.getId());
        Address entity = address_Service.upsert(address.getId(), address, req.getReq_user_id());

        address_Service.markDefault(entity, entity.getModified_by());

        return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
    }

    @PostMapping("/fetch")
    public SEResponse fetch(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            log.info("add/fetch:: API started!");
            AddressBean req = request.getGenericRequestDataObject(AddressBean.class);
            CommonUtils.extractHeaders(httpServletRequest, req);
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.MANAGE_ADDRESS);
            switch (usersBean.getRole().getUser_type()) {
                case CUSTOMER:
                case GUEST:
                    break;
                default:
                    throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }
            boolean isDefault = false;

            List<AddressDTO> listA = new ArrayList<>();
            SEFilter filterA = new SEFilter(SEFilterType.AND);
            filterA.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            filterA.addClause(WhereClause.eq(Address.Fields.entity_id, usersBean.getId()));

            List<Address> addresses = address_Service.repoFind(filterA);
            if (!CollectionUtils.isEmpty(addresses)) {

                for (Address address : addresses) {
                    if (isDefault && address.getIs_default()) {
                        address.setIs_default(false);
                        address_Service.update(address.getId(), address, req.getReq_user_id());
                    }
                    AddressDTO dto = getAddressDTO(address);
                    listA.add(dto);

                    isDefault = address.getIs_default();
                }

            }
            return SEResponse.getBasicSuccessResponseList(listA, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("add/fetch:: exception occurred");
            log.error("add/fetch:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @NotNull
    private static AddressDTO getAddressDTO(Address address) {
        AddressDTO dto = new AddressDTO();
        dto.setId(address.getId());
        dto.setStreet_1(address.getStreet_1());
        dto.setStreet_2(address.getStreet_2());
        dto.setLandmark(address.getLandmark());
        dto.setCity(address.getCity());
        dto.setState(address.getState());
        dto.setPincode(address.getPincode());
        dto.setAddress_type(address.getAddress_type().getType());
        dto.setAddress_type_desc(address.getAddress_type_desc());
        dto.setCode(address.getCode());
        dto.setLat(address.getLat());
        dto.setLng(address.getLng());
        dto.setIs_default(address.getIs_default());
        dto.setPhone_no(address.getPhone_no());
        return dto;
    }

    @PostMapping("/remove")
    public SEResponse remove(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            log.info("address/remove:: API started!");
            AddressBean req = request.getGenericRequestDataObject(AddressBean.class);
            CommonUtils.extractHeaders(httpServletRequest, req);
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.MANAGE_ADDRESS);
            switch (usersBean.getRole().getUser_type()) {
                case CUSTOMER:
                case GUEST:
                    break;
                default:
                    throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }

            AddressDTO addressDTO = req.getAddress();
            if (addressDTO == null || !StringUtils.hasText(addressDTO.getId())) {
                throw new IllegalArgumentException("Select which address to remove.");
            }

            SEFilter filterA = new SEFilter(SEFilterType.AND);
            filterA.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, addressDTO.getId()));
            filterA.addClause(WhereClause.eq(Address.Fields.entity_id, usersBean.getId()));
            Address address = address_Service.repoFindOne(filterA);
            if (address == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
            }
            if (address.isDeleted()) {
                return SEResponse.getEmptySuccessResponse(ResponseCode.ADD_ALREADY_DELETED);
            }
            address.setDeleted(true);
            address_Service.deleteOne(address.getId(), usersBean.getId());

            if (Boolean.TRUE.equals(address.getIs_default())) {
                SEFilter filter = new SEFilter(SEFilterType.AND);
                filter.addClause(WhereClause.eq(Address.Fields.entity_id, usersBean.getId()));
                filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

                Address address1 = address_Service.repoFindOne(filter);
                if (address1 != null) {
                    address_Service.markDefault(address1, usersBean.getId());
                }
            }
            return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("address/remove:: exception occurred");
            log.error("address/remove:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/mark-default")
    public SEResponse markDefault(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            log.info("address/mark-default:: API started!");
            AddressBean req = request.getGenericRequestDataObject(AddressBean.class);
            CommonUtils.extractHeaders(httpServletRequest, req);
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.MANAGE_ADDRESS);
            switch (usersBean.getRole().getUser_type()) {
                case CUSTOMER:
                case GUEST:
                    break;
                default:
                    throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }

            AddressDTO addressDTO = req.getAddress();
            if (addressDTO == null || !StringUtils.hasText(addressDTO.getId())) {
                throw new IllegalArgumentException("Select which address to remove.");
            }

            SEFilter filterA = new SEFilter(SEFilterType.AND);
            filterA.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, addressDTO.getId()));
            filterA.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            filterA.addClause(WhereClause.eq(Address.Fields.entity_id, usersBean.getId()));
            Address address = address_Service.repoFindOne(filterA);
            if (address == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
            }
            address_Service.markDefault(address, usersBean.getId());
            return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("address/mark-default:: exception occurred");
            log.error("address/mark-default:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }
}
