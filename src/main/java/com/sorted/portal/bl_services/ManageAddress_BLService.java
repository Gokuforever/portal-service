package com.sorted.portal.bl_services;

import com.sorted.commons.beans.AddressDTO;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.Address;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Role;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.Address_Service;
import com.sorted.commons.entity.service.RoleService;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.*;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.ValidationUtil;
import com.sorted.portal.request.beans.AddressBean;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
public class ManageAddress_BLService {

    private final Users_Service users_Service;
    private final RoleService roleService;
    private final Address_Service address_Service;

    @Autowired
    public ManageAddress_BLService(Users_Service users_Service, RoleService roleService, Address_Service address_Service) {
        this.users_Service = users_Service;
        this.roleService = roleService;
        this.address_Service = address_Service;
    }

    @PostMapping("/add")
    public SEResponse add(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            return createOrUpdateAddress(request, httpServletRequest, false);
        } catch (CustomIllegalArgumentsException ex) {
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

    private SEResponse createOrUpdateAddress(SERequest request, HttpServletRequest httpServletRequest, boolean isEdit) {
        log.info("createOrUpdateAddress:: method started");
        log.info("createOrUpdateAddress:: request: {}, isEdit: {}", request, isEdit);
        AddressBean req = request.getGenericRequestDataObject(AddressBean.class);
        CommonUtils.extractHeaders(httpServletRequest, req);
        UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Permission.EDIT,
                Activity.MANAGE_ADDRESS);
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


        SEFilter filterA = new SEFilter(SEFilterType.AND);
        filterA.addClause(WhereClause.eq(Address.Fields.entity_id, user_id));
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
            List<AddressDTO> listA = new ArrayList<>();
            SEFilter filterA = new SEFilter(SEFilterType.AND);
            filterA.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            filterA.addClause(WhereClause.eq(Address.Fields.entity_id, usersBean.getId()));

            List<Address> addresses = address_Service.repoFind(filterA);
            if (!CollectionUtils.isEmpty(addresses)) {
                addresses.forEach(e -> {
                    AddressDTO dto = new AddressDTO();
                    dto.setId(e.getId());
                    dto.setStreet_1(e.getStreet_1());
                    dto.setStreet_2(e.getStreet_2());
                    dto.setLandmark(e.getLandmark());
                    dto.setCity(e.getCity());
                    dto.setState(e.getState());
                    dto.setPincode(e.getPincode());
                    dto.setAddress_type(e.getAddress_type().getType());
                    dto.setAddress_type_desc(e.getAddress_type_desc());
                    dto.setCode(e.getCode());
                    dto.setLat(e.getLat());
                    dto.setLng(e.getLng());
                    dto.setIs_default(e.getIs_default());
                    listA.add(dto);
                });
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
