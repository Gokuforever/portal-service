package com.sorted.portal.bl_services;

import com.sorted.commons.beans.AddressDTO;
import com.sorted.commons.beans.Bank_Details;
import com.sorted.commons.beans.Spoc_Details;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.service.*;
import com.sorted.commons.enums.*;
import com.sorted.commons.enums.All_Status.Seller_Status;
import com.sorted.commons.enums.All_Status.User_Status;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.*;
import com.sorted.commons.helper.MailBuilder;
import com.sorted.commons.helper.Pagination;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.notifications.EmailSenderImpl;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.PasswordValidatorUtils;
import com.sorted.commons.utils.SERegExpUtils;
import com.sorted.commons.utils.ValidationUtil;
import com.sorted.portal.request.beans.BlankReqBean;
import com.sorted.portal.request.beans.CUDSellerBean;
import com.sorted.portal.request.beans.FidnSellerBean;
import com.sorted.portal.response.beans.FindResBean;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/seller")
public class ManageSeller_BLService {

    @Autowired
    private Users_Service users_Service;

    @Autowired
    private Seller_Service seller_Service;

    @Autowired
    private Address_Service address_Service;

    @Autowired
    private Plans_Service plans_Service;

    @Autowired
    private RoleService roleService;

    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private EmailSenderImpl emailSenderImpl;

    @Value("${se.default.page}")
    private int default_page;

    @Value("${se.default.size}")
    private int default_size;

    @PostMapping("/create")
    public SEResponse create(@RequestBody SERequest request, HttpServletRequest servletRequest) {
        try {
            log.info("/seller/create:: API started!");
            CUDSellerBean req = request.getGenericRequestDataObject(CUDSellerBean.class);
            CommonUtils.extractHeaders(servletRequest, req);

            UsersBean usersBean = users_Service.validateUserForActivity(req, Permission.EDIT,
                    Activity.SELLER_ONBOARDING);
            Role role = usersBean.getRole();
            if (!UserType.SUPER_ADMIN.equals(role.getUser_type())) {
                throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }
            validateSellerReq(req);

            Address address = ValidationUtil.validateAddress(req.getAddress(), new Address());

            List<Spoc_Details> spoc_details = req.getSpoc_details();
            Spoc_Details primary_spoc = validateSPOC(spoc_details);
            List<String> unique_phone = spoc_details.parallelStream().map(Spoc_Details::getMobile_no).distinct()
                    .toList();
            Bank_Details bank_details = req.getBank_details();
            if (bank_details != null) {
                ValidationUtil.validateBankDetails(bank_details);
            }
            List<String> serviceable_pincodes = req.getServiceable_pincodes();
            if (CollectionUtils.isEmpty(serviceable_pincodes)) {
                throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SERVICEABLE_PINCODE);
            }
            for (String pincode : serviceable_pincodes) {
                if (!SERegExpUtils.isPincode(pincode)) {
                    throw new CustomIllegalArgumentsException(ResponseCode.INVALID_PINCODE);
                }
            }

            SEFilter filterS = new SEFilter(SEFilterType.AND);
            filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            Map<String, Object> map = new HashMap<>();
            map.put(Spoc_Details.Fields.mobile_no, unique_phone);
            map.put(Spoc_Details.Fields.primary, true);
            filterS.addClause(WhereClause.elem_match(Seller.Fields.spoc_details, map));

            Seller seller = seller_Service.repoFindOne(filterS);
            if (seller != null) {
                throw new CustomIllegalArgumentsException(ResponseCode.DUPLICATE_SE_MOBILE);
            }
            seller = new Seller();

            SEFilter filterU = new SEFilter(SEFilterType.AND);
            filterU.addClause(WhereClause.eq(Users.Fields.mobile_no, primary_spoc.getMobile_no()));
            filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            long duplicate = users_Service.countByFilter(filterU);
            if (duplicate > 0) {
                throw new CustomIllegalArgumentsException(ResponseCode.DUPLICATE_USER_MOBILE);
            }
            filterU = new SEFilter(SEFilterType.AND);
            filterU.addClause(WhereClause.eq(Users.Fields.email_id, primary_spoc.getEmail_id()));
            filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            duplicate = users_Service.countByFilter(filterU);
            if (duplicate > 0) {
                throw new CustomIllegalArgumentsException(ResponseCode.DUPLICATE_USER_EMAIL);
            }
            String name = CommonUtils.toTitleCase(req.getName());
            String pan_no = req.getPan_no();
            seller.setBusiness_name(name);
            seller.setSpoc_details(spoc_details);
            seller.setServiceable_pincodes(serviceable_pincodes);
            if (StringUtils.hasText(pan_no)) {
                seller.setCompany_pan(pan_no);
            }
            if (bank_details != null) {
                seller.setBank_details(bank_details);
            }
            SEFilter filterP = new SEFilter(SEFilterType.AND);
            filterP.addClause(WhereClause.eq(Plans.Fields.name, Defaults.DEFAULT_SELLER_PLAN));
            filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Plans plan = plans_Service.repoFindOne(filterP);
            if (plan == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_DEF_SELLER_PLAN);
            }

            Seller seller_record = seller_Service.upsert(seller.getId(), seller, usersBean.getId());

            Users user = new Users();
            user.setFirst_name(primary_spoc.getFirst_name());
            user.setLast_name(primary_spoc.getLast_name());
            user.setMobile_no(primary_spoc.getMobile_no());
            user.setEmail_id(primary_spoc.getEmail_id());
            user.setIs_verified(true);
            user.setStatus(User_Status.ACTIVE.getId());
            String generatePassword = PasswordValidatorUtils.generatePassword();
            String encode = passwordEncoder.encode(generatePassword);
            user.setPassword(encode);

            List<Role> roles = plan.getRoles();
            roles.forEach(e -> {
                e.setSeller_code(seller_record.getCode());
                e.setSeller_id(seller_record.getId());
                e.setUser_type(UserType.SELLER);
                e.setUser_type_id(UserType.SELLER.getId());
                Role temp = roleService.create(e, usersBean.getId());
                if (e.getName().equals("SHOP OWNER")) {
                    user.setRole_id(temp.getId());
                }
            });

            users_Service.create(user, usersBean.getId());

            address.setUser_type(UserType.SELLER);
            address.setEntity_id(seller_record.getId());
            address = address_Service.create(address, req.getReq_user_id());

            seller_record.setAddress_id(address.getId());
            seller_Service.update(seller.getId(), seller, usersBean.getId());

            String first_name = primary_spoc.getFirst_name();
            String mobile_no = primary_spoc.getMobile_no();

            String cont = first_name + "|" + mobile_no + "|" + generatePassword;
            MailBuilder builder = new MailBuilder();
            builder.setTo(primary_spoc.getEmail_id());
            builder.setContent(cont);
            builder.setTemplate(MailTemplate.SELLER_WELCOME_MAIL);
            emailSenderImpl.sendEmailHtmlTemplate(builder);

            log.info("/seller/create:: API ended");
            return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/seller/create:: exception occurred");
            log.error("/seller/create:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/edit")
    public SEResponse edit(@RequestBody SERequest request, HttpServletRequest servletRequest) {
        try {
            log.info("/seller/edit:: API started!");
            CUDSellerBean req = request.getGenericRequestDataObject(CUDSellerBean.class);
            CommonUtils.extractHeaders(servletRequest, req);

            UsersBean usersBean = users_Service.validateUserForActivity(req, Permission.EDIT,
                    Activity.SELLER_ONBOARDING);
            if (!UserType.SUPER_ADMIN.equals(usersBean.getRole().getUser_type())) {
                throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }
            if (!StringUtils.hasText(req.getSeller_id())) {
                throw new CustomIllegalArgumentsException(ResponseCode.SELLER_ID_MANDATE);
            }
            validateSellerReq(req);

            SEFilter filterDbS = new SEFilter(SEFilterType.AND);
            filterDbS.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getSeller_id()));
            filterDbS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            Seller seller = seller_Service.repoFindOne(filterDbS);
            if (seller == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.SELLER_NOT_FOUND);
            }
            Optional<Spoc_Details> result = seller.getSpoc_details().stream().filter(Spoc_Details::isPrimary)
                    .findFirst();
            int db_spocHash = getSpocHashCode(result.get());

            List<Spoc_Details> spoc_details = req.getSpoc_details();
            Spoc_Details primary_spoc = validateSPOC(spoc_details);
            int req_spocHash = getSpocHashCode(primary_spoc);

            if (db_spocHash != req_spocHash) {

                List<String> unique_phone = spoc_details.parallelStream().map(Spoc_Details::getMobile_no).distinct()
                        .toList();

                SEFilter filterS = new SEFilter(SEFilterType.AND);
                filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
                filterS.addClause(WhereClause.notEq(BaseMongoEntity.Fields.id, seller.getId()));
                Map<String, Object> map = new HashMap<>();
                map.put(Spoc_Details.Fields.mobile_no, unique_phone);
                map.put(Spoc_Details.Fields.primary, true);
                filterS.addClause(WhereClause.elem_match(Seller.Fields.spoc_details, map));
                Seller temp_seller = seller_Service.repoFindOne(filterS);
                if (temp_seller != null) {
                    throw new CustomIllegalArgumentsException(ResponseCode.DUPLICATE_SE_MOBILE);
                }

                SEFilter filterR = new SEFilter(SEFilterType.AND);
                filterR.addClause(WhereClause.eq(Role.Fields.seller_id, seller.getId()));
                filterR.addClause(WhereClause.eq(Role.Fields.user_type, UserType.SELLER.name()));
                Role role = roleService.repoFindOne(filterR);
                if (role == null) {
                    throw new CustomIllegalArgumentsException(ResponseCode.ROLE_MISSING);
                }

                /*
                 * fetching user details on the basis of role as there is One Role and One User
                 * per Seller
                 */

                SEFilter filterU = new SEFilter(SEFilterType.AND);
                filterU.addClause(WhereClause.eq(Users.Fields.mobile_no, primary_spoc.getMobile_no()));
                filterU.addClause(WhereClause.notEq(Users.Fields.role_id, role.getId()));
                filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
                long duplicate = users_Service.countByFilter(filterU);
                if (duplicate > 0) {
                    throw new CustomIllegalArgumentsException(ResponseCode.DUPLICATE_USER_MOBILE);
                }
                filterU = new SEFilter(SEFilterType.AND);
                filterU.addClause(WhereClause.eq(Users.Fields.email_id, primary_spoc.getEmail_id()));
                filterU.addClause(WhereClause.notEq(Users.Fields.role_id, role.getId()));
                filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
                duplicate = users_Service.countByFilter(filterU);
                if (duplicate > 0) {
                    throw new CustomIllegalArgumentsException(ResponseCode.DUPLICATE_USER_EMAIL);
                }

                filterU = new SEFilter(SEFilterType.AND);
                filterU.addClause(WhereClause.eq(Users.Fields.role_id, role.getId()));
                Users user = users_Service.repoFindOne(filterU);
                if (user == null) {
                    throw new CustomIllegalArgumentsException(ResponseCode.USER_NOT_FOUND);
                }

                user.setFirst_name(primary_spoc.getFirst_name());
                user.setLast_name(primary_spoc.getLast_name());
                user.setMobile_no(primary_spoc.getMobile_no());
                user.setEmail_id(primary_spoc.getEmail_id());
//				String generatePassword = PasswordValidatorUtils.generatePassword();
//				String encode = passwordEncoder.encode(generatePassword);
//				user.setPassword(encode);
                users_Service.update(user.getId(), user, usersBean.getId());

            }

            Bank_Details bank_details = req.getBank_details();
            if (bank_details != null) {
                ValidationUtil.validateBankDetails(bank_details);
            }
            List<String> serviceable_pincodes = req.getServiceable_pincodes();
            if (CollectionUtils.isEmpty(serviceable_pincodes)) {
                throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SERVICEABLE_PINCODE);
            }
            for (String pincode : serviceable_pincodes) {
                if (!SERegExpUtils.isPincode(pincode)) {
                    throw new CustomIllegalArgumentsException(ResponseCode.INVALID_PINCODE);
                }
            }

            String name = CommonUtils.toTitleCase(req.getName());
            String pan_no = req.getPan_no();
            seller.setBusiness_name(name);
            seller.setSpoc_details(spoc_details);
            seller.setServiceable_pincodes(serviceable_pincodes);
            seller.setCompany_pan(pan_no);
            seller.setBank_details(bank_details);
            seller_Service.update(seller.getId(), seller, usersBean.getId());

            SEFilter filterA = new SEFilter(SEFilterType.AND);
            filterA.addClause(WhereClause.eq(Address.Fields.entity_id, seller.getId()));
            filterA.addClause(WhereClause.eq(Address.Fields.user_type, UserType.SELLER.name()));
            Address dbAddress = address_Service.repoFindOne(filterA);
            if (dbAddress == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.ADDRESS_NOT_FOUND);
            }
            int db_hash = getSellerAddresshashCode(dbAddress);

            Address address = ValidationUtil.validateAddress(req.getAddress(), dbAddress);
            address.setUser_type(UserType.SELLER);
            address.setEntity_id(seller.getId());
            int req_hash = getSellerAddresshashCode(address);
            if (db_hash != req_hash) {
                address_Service.update(dbAddress.getId(), address, req.getReq_user_id());
            }
            log.info("/seller/edit:: API ended");
            return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/seller/edit:: exception occurred");
            log.error("/seller/edit:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/changeStatus")
    public SEResponse changeStatus(@RequestBody SERequest request, HttpServletRequest servletRequest) {
        try {
            log.info("/seller/changeStatus:: API started!");
            CUDSellerBean req = request.getGenericRequestDataObject(CUDSellerBean.class);
            CommonUtils.extractHeaders(servletRequest, req);
            UsersBean usersBean = users_Service.validateUserForActivity(req, Permission.EDIT,
                    Activity.SELLER_ONBOARDING, Activity.STORE_MANAGEMENT);
            Seller_Status status = Seller_Status.getById(req.getStatus_id());
            if (status == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SELLER_STATUS);
            }
            SEFilter filterS = new SEFilter(SEFilterType.AND);
            switch (usersBean.getRole().getUser_type()) {
                case SUPER_ADMIN: {
                    if (!StringUtils.hasText(req.getSeller_id())) {
                        throw new CustomIllegalArgumentsException(ResponseCode.SELLER_ID_MANDATE);
                    }
                    filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getSeller_id()));
                    break;
                }
                case SELLER: {
                    if (!(status.equals(Seller_Status.ACTIVE) || status.equals(Seller_Status.INACTIVE))) {
                        throw new CustomIllegalArgumentsException(ResponseCode.ACTION_NOT_ALLOWED);
                    }
                    filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, usersBean.getSeller().getId()));
                    break;
                }
                default: {
                    throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
                }
            }
            filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            Seller seller = seller_Service.repoFindOne(filterS);
            if (seller == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.SELLER_NOT_FOUND);
            }
            if (seller.getStatus().equals(status)) {
                throw new CustomIllegalArgumentsException(ResponseCode.SELLER_STATUS_UNCHANGED);
            }
            seller.setStatus(status);
            seller_Service.update(seller.getId(), seller, req.getReq_user_id());
            log.info("/seller/changeStatus:: API ended");
            return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/seller/changeStatus:: exception occurred");
            log.error("/seller/changeStatus:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/resend")
    public SEResponse resend(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            log.info("/seller/resend API started.");
            FidnSellerBean req = request.getGenericRequestDataObject(FidnSellerBean.class);
            CommonUtils.extractHeaders(httpServletRequest, req);
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(),
                    Activity.SELLER_MANAGEMENT);

            Role role = usersBean.getRole();
            if (role.getUser_type() != UserType.SUPER_ADMIN) {
                throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }

            SEFilter filterS = new SEFilter(SEFilterType.AND);
            filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getId()));
            filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Seller seller = seller_Service.repoFindOne(filterS);
            if (seller == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
            }
            Optional<Spoc_Details> optional = seller.getSpoc_details().stream().filter(Spoc_Details::isPrimary).findFirst();
            if (optional.isEmpty()) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PRIMARY_SPOC);
            }
            Spoc_Details spoc_Details = optional.get();
            String mobile_no = spoc_Details.getMobile_no();
            SEFilter filterU = new SEFilter(SEFilterType.AND);
            filterU.addClause(WhereClause.eq(Users.Fields.mobile_no, mobile_no));
            filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Users users = users_Service.repoFindOne(filterU);
            if (users == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
            }

            if (users.isPass_changed()) {
                throw new CustomIllegalArgumentsException(ResponseCode.PASSWORD_ALREADY_RESET);
            }

            String generatePassword = PasswordValidatorUtils.generatePassword();
            String encode = passwordEncoder.encode(generatePassword);
            users.setPassword(encode);

            users_Service.update(users.getId(), users, usersBean.getId());

            String first_name = spoc_Details.getFirst_name();

            String cont = first_name + "|" + mobile_no + "|" + generatePassword;
            MailBuilder builder = new MailBuilder();
            builder.setTo(spoc_Details.getEmail_id());
            builder.setContent(cont);
            builder.setTemplate(MailTemplate.SELLER_WELCOME_MAIL);
            emailSenderImpl.sendEmailHtmlTemplate(builder);

            return SEResponse.getEmptySuccessResponse(ResponseCode.RESEND_SUCCESS);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/seller/resend:: exception occurred");
            log.error("/seller/resend:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/find")
    public SEResponse find(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            log.info("find seller API started.");
            FidnSellerBean req = request.getGenericRequestDataObject(FidnSellerBean.class);
            CommonUtils.extractHeaders(httpServletRequest, req);
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(),
                    Activity.SELLER_MANAGEMENT);

            Role role = usersBean.getRole();
            switch (role.getUser_type()) {
                case SUPER_ADMIN:
                    break;
                default:
                    throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }

            FindResBean bean = new FindResBean();

            SEFilter filterS = new SEFilter(SEFilterType.AND);
            if (StringUtils.hasText(req.getName())) {
                filterS.addClause(WhereClause.like(Seller.Fields.business_name, req.getName().trim()));
            }

            filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            OrderBy orderBy = new OrderBy(BaseMongoEntity.Fields.modification_date, SortOrder.DESC);
            filterS.setOrderBy(orderBy);

            int page = req.getPage();
            int size = req.getSize();
            if (size < 1) {
                page = default_page;
                size = default_size;
            }
            if (page == 0) {
                long total_count = seller_Service.countByFilter(filterS);
                if (total_count == 0) {
                    return SEResponse.getBasicSuccessResponseObject(bean, ResponseCode.NO_RECORD);
                }
                bean.setTotal_count(total_count);
            }
            Pagination pagination = new Pagination(page, size);

            filterS.setPagination(pagination);

            List<Seller> listS = seller_Service.repoFind(filterS);
            if (CollectionUtils.isEmpty(listS)) {
                return SEResponse.getBasicSuccessResponseObject(bean, ResponseCode.SUCCESSFUL);
            }
            Map<String, Address> mapAdd = new HashMap<>();

            List<String> seller_ids = listS.stream().map(Seller::getId).toList();
            SEFilter filterSE = new SEFilter(SEFilterType.AND);
            filterSE.addClause(WhereClause.in(Address.Fields.entity_id, seller_ids));
            filterSE.addClause(WhereClause.eq(Address.Fields.user_type, UserType.SELLER.name()));
            filterSE.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, false));

            List<Address> listAdd = address_Service.repoFind(filterSE);
            if (!CollectionUtils.isEmpty(listAdd)) {
                mapAdd.putAll(listAdd.stream().collect(Collectors.toMap(Address::getEntity_id, e -> e)));
            }

            List<String> primary_contacts = listS.stream().flatMap(e -> e.getSpoc_details().stream())
                    .filter(Spoc_Details::isPrimary).map(Spoc_Details::getMobile_no).filter(Objects::nonNull).distinct().toList();

            SEFilter filterU = new SEFilter(SEFilterType.AND);
            filterU.addClause(WhereClause.in(Users.Fields.mobile_no, primary_contacts));
            filterU.addClause(WhereClause.eq(Users.Fields.pass_changed, false));
            filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            List<Users> listPrimary = users_Service.repoFind(filterU);

            Map<String, Boolean> mapIsResend = new HashMap<>();
            if (!CollectionUtils.isEmpty(listPrimary)) {
                List<String> list = listPrimary.stream().map(Users::getMobile_no).toList();
                mapIsResend.putAll(listS.stream().collect(Collectors.toMap(e -> e.getId(), e -> e.getSpoc_details()
                        .stream().anyMatch(s -> s.isPrimary() && list.contains(s.getMobile_no())))));
            }

            List<CUDSellerBean> resList = new ArrayList<>();

            for (Seller seller : listS) {
                CUDSellerBean tempBean = new CUDSellerBean();
                tempBean.setName(seller.getBusiness_name());
                tempBean.setSeller_id(seller.getId());
                if (mapAdd.containsKey(seller.getId())) {
                    Address address = mapAdd.get(seller.getId());
                    if (address == null) {
                        continue;
                    }

                    AddressDTO address2 = new AddressDTO();
                    address2.setStreet_1(address.getStreet_1());
                    address2.setStreet_2(address.getStreet_2());
                    address2.setLandmark(address.getLandmark());
                    address2.setCity(address.getCity());
                    address2.setState(address.getState());
                    address2.setPincode(address.getPincode());
                    address2.setAddress_type(address.getAddress_type().getType());
                    address2.setAddress_type_desc(address.getAddress_type_desc());
                    tempBean.setAddress(address2);
                }
                tempBean.setPan_no(seller.getCompany_pan());
                tempBean.setSpoc_details(seller.getSpoc_details());
                tempBean.setBank_details(seller.getBank_details());
                tempBean.setServiceable_pincodes(seller.getServiceable_pincodes());
                tempBean.setStatus_id(seller.getStatus().getId());
                boolean resend = mapIsResend.containsKey(seller.getId()) && mapIsResend.get(seller.getId());
                tempBean.setResend(resend);
                resList.add(tempBean);
            }

            bean.setList(resList);
            return SEResponse.getBasicSuccessResponseObject(bean, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/seller/find:: exception occurred");
            log.error("/seller/find:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/findOne")
    public SEResponse findOne(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            BlankReqBean req = request.getGenericRequestDataObject(BlankReqBean.class);
            CommonUtils.extractHeaders(httpServletRequest, req);
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.USER_PROFILE);
            Role role = usersBean.getRole();
            if (role.getUser_type() != UserType.SELLER) {
                throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }
            Seller seller = usersBean.getSeller();
            SEFilter filterSE = new SEFilter(SEFilterType.AND);
            filterSE.addClause(WhereClause.eq(Address.Fields.entity_id, seller.getId()));
            filterSE.addClause(WhereClause.eq(Address.Fields.user_type, UserType.SELLER.name()));
            filterSE.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, false));

            Address address = address_Service.repoFindOne(filterSE);

            CUDSellerBean tempBean = new CUDSellerBean();
            tempBean.setName(seller.getBusiness_name());
            tempBean.setSeller_id(seller.getId());

            if (address != null) {
                AddressDTO address2 = new AddressDTO();
                address2.setStreet_1(address.getStreet_1());
                address2.setStreet_2(address.getStreet_2());
                address2.setLandmark(address.getLandmark());
                address2.setCity(address.getCity());
                address2.setState(address.getState());
                address2.setPincode(address.getPincode());
                address2.setAddress_type(address.getAddress_type().getType());
                address2.setAddress_type_desc(address.getAddress_type_desc());
                tempBean.setAddress(address2);
            }

            tempBean.setPan_no(seller.getCompany_pan());
            tempBean.setSpoc_details(seller.getSpoc_details());
            tempBean.setBank_details(seller.getBank_details());
            tempBean.setServiceable_pincodes(seller.getServiceable_pincodes());
            tempBean.setStatus_id(seller.getStatus().getId());

            return SEResponse.getBasicSuccessResponseObject(tempBean, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/seller/findOne:: exception occurred");
            log.error("/seller/findOne:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    private void validateSellerReq(CUDSellerBean req) {
        if (!StringUtils.hasText(req.getName())) {
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SELLER_NAME);
        }
        if (!SERegExpUtils.standardTextValidation(req.getName())) {
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SELLER_NAME);
        }
//		if (!StringUtils.hasText(req.getPan_no())) {
//			throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_PAN_NO);
//		}
//		if (!SERegExpUtils.isPan(req.getPan_no())) {
//			throw new CustomIllegalArgumentsException(ResponseCode.INVALID_PAN_NO);
//		}
        if (StringUtils.hasText(req.getPan_no()) && !SERegExpUtils.isPan(req.getPan_no())) {
            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_PAN_NO);
        }
        if (CollectionUtils.isEmpty(req.getSpoc_details())) {
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SPOC);
        }
        if (req.getAddress() == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.MISSING_ADDRESS);
        }
    }

    private Spoc_Details validateSPOC(List<Spoc_Details> spoc_details) {
        List<String> unique_phone = new ArrayList<>();
        Spoc_Details primary_spoc = null;
        for (Spoc_Details spoc : spoc_details) {
            if (!StringUtils.hasText(spoc.getFirst_name())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SPOC_FNAME);
            }
            if (!SERegExpUtils.standardTextValidation(spoc.getFirst_name())) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SPOC_FNAME);
            }
            if (!StringUtils.hasText(spoc.getLast_name())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SPOC_LNAME);
            }
            if (!SERegExpUtils.standardTextValidation(spoc.getLast_name())) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SPOC_LNAME);
            }
            if (!StringUtils.hasText(spoc.getEmail_id())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SPOC_MAIL);
            }
            if (!SERegExpUtils.isEmail(spoc.getEmail_id())) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SPOC_MAIL);
            }
            if (!StringUtils.hasText(spoc.getMobile_no())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SPOC_PHONE);
            }
            if (!SERegExpUtils.isMobileNo(spoc.getMobile_no())) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SPOC_PHONE);
            }
            if (!StringUtils.hasText(spoc.getDesignation())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SPOC_DESIGNATION);
            }
            if (!SERegExpUtils.standardTextValidation(spoc.getDesignation())) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SPOC_DESIGNATION);
            }
            if (unique_phone.contains(spoc.getMobile_no())) {
                throw new CustomIllegalArgumentsException(ResponseCode.DUPLICATE_SPOC_PHONE);
            }
            unique_phone.add(spoc.getMobile_no());
            if (spoc.isPrimary() && primary_spoc == null) {
                primary_spoc = spoc;
            } else {
                spoc.setPrimary(false);
            }
        }
        if (primary_spoc == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.MARK_PRIMARY);
        }
        return primary_spoc;
    }

    private int getSpocHashCode(Spoc_Details s) {
        return Objects.hash(s.getFirst_name(), s.getLast_name(), s.getMobile_no(), s.getEmail_id());
    }

    private int getSellerAddresshashCode(Address a) {
        return Objects.hash(a.getStreet_1(), a.getStreet_2(), a.getLandmark(), a.getCity(), a.getState(),
                a.getPincode(), a.getAddress_type(), a.getAddress_type_desc());
    }

}
