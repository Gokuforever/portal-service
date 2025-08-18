package com.sorted.portal.bl_services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sorted.commons.beans.*;
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.mongo.Category_Master.SubCategory;
import com.sorted.commons.entity.service.*;
import com.sorted.commons.enums.*;
import com.sorted.commons.exceptions.AccessDeniedException;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.*;
import com.sorted.commons.helper.Pagination;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.helper.SearchHistoryAsyncHelper;
import com.sorted.commons.repository.mongo.ProductRepository;
import com.sorted.commons.utils.AwsS3Service;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.PorterUtility;
import com.sorted.commons.utils.SERegExpUtils;
import com.sorted.portal.assisting.beans.ProductDetailsBean;
import com.sorted.portal.assisting.beans.ProductDetailsBean.CartDetails;
import com.sorted.portal.assisting.beans.ProductDetailsBean.CartDetails.CartDetailsBuilder;
import com.sorted.portal.enums.OrderItemsProperties;
import com.sorted.portal.enums.OrderProperties;
import com.sorted.portal.enums.ReportType;
import com.sorted.portal.request.beans.BlankReqBean;
import com.sorted.portal.request.beans.BulkEditProductReqBean;
import com.sorted.portal.request.beans.FindProductBean;
import com.sorted.portal.request.beans.RandomProductReqBean;
import com.sorted.portal.response.beans.OrderItemReportsDTO;
import com.sorted.portal.response.beans.OrderReportDTO;
import com.sorted.portal.service.ExcelGenerationUtility;
import com.sorted.portal.service.FileGeneratorUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Log4j2
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ManageProduct_BLService {

    private final ProductService productService;
    private final Cart_Service cart_Service;
    private final Varient_Mapping_Service varient_Mapping_Service;
    private final Category_MasterService category_MasterService;
    private final Users_Service users_Service;
    private final Seller_Service seller_Service;
    private final SearchHistoryAsyncHelper searchHistoryAsyncHelper;
    private final PorterUtility porterUtility;
    private final AwsS3Service awsS3Service;
    @Value("${se.default.page}")
    private int defaultPage;
    @Value("${se.default.size}")
    private int defaultSize;
    @Value("${se.default.seller:6870158e00e94802261d857a}")
    private String defaultSeller;
    private final StoreActivityService storeActivityService;
    private final ProductRepository productRepository;
    private final EducationCategoriesService educationCategoriesService;


    @GetMapping("/curated")
    public SEResponse getCuratedProduct(@RequestBody SERequest request, HttpServletRequest httpServletRequest) throws JsonProcessingException {
        BlankReqBean req = request.getGenericRequestDataObject(BlankReqBean.class);
        CommonUtils.extractHeaders(httpServletRequest, req);
        UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(),
                Activity.PRODUCTS);
        Role role = usersBean.getRole();
        UserType user_type = role.getUser_type();
        switch (user_type) {
            case GUEST, CUSTOMER:
                break;
            default:
                throw new AccessDeniedException();
        }
        SEFilter filter = new SEFilter(SEFilterType.AND);
        String nearestSeller = usersBean.getNearestSeller();
        if (StringUtils.hasText(nearestSeller)) {
            boolean storeOperational = storeActivityService.isStoreOperational(nearestSeller);
            if (!storeOperational) {
                NearestSellerRes nearestSellerRes = porterUtility.getNearestSeller(usersBean.getNearestPincode(), usersBean.getMobile_no(), usersBean.getFirst_name(), usersBean.getId());
                if (!nearestSellerRes.getSeller_id().equals(nearestSeller)) {
                    nearestSeller = nearestSellerRes.getSeller_id();
                    @SuppressWarnings("OptionalGetWithoutIsPresent")
                    Users users = users_Service.findById(usersBean.getId()).get();
                    users.setNearestSeller(nearestSeller);
                    users_Service.update(users.getId(), users, users.getId());
                }
            }
            filter.addClause(WhereClause.eq(Products.Fields.seller_id, nearestSeller));
        }
        EducationCategoryBean educationDetails = usersBean.getEducationDetails();

        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        if (educationDetails != null) {
            educationDetails.getId();
            Optional<EducationCategories> optional = educationCategoriesService.findById(educationDetails.getId());
            if (optional.isPresent()) {
                EducationCategories educationCategories = optional.get();
                List<String> categoryIds = educationCategories.getCategory_ids();
                if (!CollectionUtils.isEmpty(categoryIds)) {
                    filter.addClause(WhereClause.in(Products.Fields.category_id, categoryIds));
                }
            }
            List<EducationCategoryField> fields = educationDetails.getFields();
            for (EducationCategoryField field : fields) {
                Map<String, Object> map = new HashMap<>();
                map.put(SelectedSubCatagories.Fields.sub_category, field.getAlias());
                map.put(SelectedSubCatagories.Fields.selected_attributes, field.getOptions());
                filter.addClause(WhereClause.elem_match(Products.Fields.selected_sub_catagories, map));
            }
        }

        List<Products> products = productService.repoFind(filter);
        List<ProductDetailsBean> productDetailsBeans = this.convertToBean(products);
        log.info("Successfully processed curated product request, returning {} products", productDetailsBeans.size());
        return SEResponse.getBasicSuccessResponseList(productDetailsBeans, ResponseCode.SUCCESSFUL);
    }

    @PostMapping("/random")
    public SEResponse getRandomProduct(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            RandomProductReqBean req = request.getGenericRequestDataObject(RandomProductReqBean.class);
            log.debug("Received request to get random products with params: {}", req);

            CommonUtils.extractHeaders(httpServletRequest, req);
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.PRODUCTS);
            Role role = usersBean.getRole();
            UserType user_type = role.getUser_type();
            log.debug("User type: {}", user_type);

            switch (user_type) {
                case GUEST, CUSTOMER:
                    break;
                default:
                    log.warn("Access denied for user type: {}", user_type);
                    throw new AccessDeniedException();
            }

            String categoryId = req.getCategory_id();
            long count = req.getCount();
            log.debug("Category ID: {}, Requested count: {}", categoryId, count);

            SEFilter filter = new SEFilter(SEFilterType.AND);
            filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            if (StringUtils.hasText(categoryId)) {
                filter.addClause(WhereClause.eq(Products.Fields.category_id, categoryId));
            }

            if (count < 0 || count > 100) {
                log.debug("Count out of bounds ({}), defaulting to 12", count);
                count = 12;
            }

            List<Products> randomProducts = productRepository.getRandomProducts(filter, count);
            log.debug("Retrieved {} random products", randomProducts.size());

            List<ProductDetailsBean> productDetailsBeans = this.convertToBean(randomProducts);
            log.info("Successfully processed random product request, returning {} products", productDetailsBeans.size());

            return SEResponse.getBasicSuccessResponseList(productDetailsBeans, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            log.error("/random:: error occurred:: {}", ex.getMessage());
            throw ex;
        } catch (Exception e) {
            log.error("/random:: error occurred:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }


    @PostMapping("/bulk/create")
    public SEResponse bulkCreate(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            log.info("/bulk/create:: AOI started");
            AddBulkProductReqBean req = request.getGenericRequestDataObject(AddBulkProductReqBean.class);
            CommonUtils.extractHeaders(httpServletRequest, req);
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Permission.EDIT,
                    Activity.INVENTORY_MANAGEMENT);

            Role role = usersBean.getRole();
            UserType user_type = role.getUser_type();
            Seller seller;
            switch (user_type) {
                case SELLER:
                    seller = usersBean.getSeller();
                    break;
                case SUPER_ADMIN:
                    if (!StringUtils.hasText(req.getSeller_id())) {
                        throw new CustomIllegalArgumentsException(ResponseCode.PLEASE_SELECT_SELLER);
                    }
                    SEFilter filterS = new SEFilter(SEFilterType.AND);
                    filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getSeller_id()));
                    filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

                    seller = seller_Service.repoFindOne(filterS);
                    if (seller == null) {
                        throw new CustomIllegalArgumentsException(ResponseCode.SELLER_NOT_FOUND);
                    }
                    break;
                default:
                    throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }
            List<ProductReqBean> products = req.getProducts();
            if (CollectionUtils.isEmpty(products)) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PRODUCTS);
            }

            this.validateRequestForBulk(products);
            List<String> categoryIds = products.stream().map(ProductReqBean::getCategory_id).distinct().toList();

            Map<String, Category_Master> mapC = this.getStringCategoryMasterMap(categoryIds);

            List<Products> listP = products.parallelStream()
                    .map(productReqBean -> {
                        if (productReqBean.getGroup_id() == null || productReqBean.getGroup_id() <= 0) {
                            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_GROUP);
                        }
                        Category_Master category_Master = mapC.get(productReqBean.getCategory_id());
                        Map<String, List<String>> mapSC = this.getSubCategoriesMap(category_Master, productReqBean.getGroup_id());
                        List<SelectedSubCatagories> listSC = this.buildSelectedSubCategories(productReqBean, mapSC);
                        this.validateMandatorySubCategories(category_Master, listSC, productReqBean.getGroup_id());
                        BigDecimal mrp = new BigDecimal(productReqBean.getMrp());
                        BigDecimal sp = new BigDecimal(productReqBean.getSelling_price());
                        Products product = new Products();
                        product.setName(productReqBean.getName());
                        product.setMrp(CommonUtils.rupeeToPaise(mrp));
                        product.setSelling_price(CommonUtils.rupeeToPaise(sp));
                        product.setSelected_sub_catagories(listSC);
                        product.setSeller_id(seller.getId());
                        product.setSeller_code(seller.getCode());
                        product.setCategory_id(category_Master.getId());
                        product.setQuantity(Long.valueOf(productReqBean.getQuantity()));
                        product.setGroup_id(productReqBean.getGroup_id());
                        product.setDescription(
                                StringUtils.hasText(productReqBean.getDescription()) ? productReqBean.getDescription() : null);
                        product.setIs_secure(productReqBean.getIs_secure() != null && productReqBean.getIs_secure());
                        if (!CollectionUtils.isEmpty(productReqBean.getMedia())) {
                            product.setMedia(getMediaList(productReqBean.getMedia()));
                        }
                        return product;
                    })
                    .collect(Collectors.toList());

            productService.bulkCreate(listP, usersBean.getId());

            return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/bulk/create:: error occurred:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @NotNull
    private Map<String, Category_Master> getStringCategoryMasterMap(List<String> categoryIds) {
        SEFilter filterC = new SEFilter(SEFilterType.AND);
        filterC.addClause(WhereClause.in(BaseMongoEntity.Fields.id, categoryIds));
        filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Category_Master> categoryMasters = category_MasterService.repoFind(filterC);
        if (CollectionUtils.isEmpty(categoryMasters)) {
            throw new CustomIllegalArgumentsException(ResponseCode.CATEGORY_NOT_FOUND);
        }

        return categoryMasters.stream().collect(Collectors.toMap(Category_Master::getId, Function.identity()));
    }

    @PostMapping("/create")
    public SEResponse create(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            log.info("/product/create API started");
            ProductReqBean req = request.getGenericRequestDataObject(ProductReqBean.class);
            CommonUtils.extractHeaders(httpServletRequest, req);
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Permission.EDIT,
                    Activity.INVENTORY_MANAGEMENT);

            Role role = usersBean.getRole();
            UserType user_type = role.getUser_type();
            Seller seller;
            switch (user_type) {
                case SELLER:
                    seller = usersBean.getSeller();
                    break;
                case SUPER_ADMIN:
                    if (!StringUtils.hasText(req.getSeller_id())) {
                        throw new CustomIllegalArgumentsException(ResponseCode.PLEASE_SELECT_SELLER);
                    }
                    SEFilter filterS = new SEFilter(SEFilterType.AND);
                    filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getSeller_id()));
                    filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

                    seller = seller_Service.repoFindOne(filterS);
                    if (seller == null) {
                        throw new CustomIllegalArgumentsException(ResponseCode.SELLER_NOT_FOUND);
                    }
                    break;
                default:
                    throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }

            this.validateRequest(req, false);

            Category_Master category_Master = this.getCategoryMaster(req.getCategory_id());

            Map<String, List<String>> mapSC = this.getSubCategoriesMap(category_Master, req.getGroup_id());

            List<SelectedSubCatagories> listSC = this.buildSelectedSubCategories(req, mapSC);

            this.validateMandatorySubCategories(category_Master, listSC, req.getGroup_id());

            String varient_mapping_id = this.upsertAndGetVariantMappingId(req, usersBean, role);

            BigDecimal mrp = new BigDecimal(req.getMrp());
            BigDecimal sp = new BigDecimal(req.getSelling_price());
            Products product = new Products();
            product.setName(req.getName());
            product.setMrp(CommonUtils.rupeeToPaise(mrp));
            product.setSelling_price(CommonUtils.rupeeToPaise(sp));
            product.setSelected_sub_catagories(listSC);
            product.setSeller_id(seller.getId());
            product.setSeller_code(seller.getCode());
            product.setCategory_id(category_Master.getId());
            product.setQuantity(Long.valueOf(req.getQuantity()));
            product.setVarient_mapping_id(varient_mapping_id);
            product.setGroup_id(req.getGroup_id());
            product.setDescription(StringUtils.hasText(req.getDescription()) ? req.getDescription() : null);
            product.setIs_secure(req.getIs_secure() != null && req.getIs_secure());

            List<Media> mediaList = getMediaList(req.getMedia());
            product.setMedia(mediaList);


            Products create = productService.create(product, usersBean.getId());
            return SEResponse.getBasicSuccessResponseObject(create, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/product/create:: error occurred:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }


    @NotNull
    private static List<Media> getMediaList(List<Media> media) {
        return media.stream().map(e -> Media.builder()
                .cdn_url(StringUtils.hasText(e.getCdn_url()) ? e.getCdn_url() : "")
                .order(e.getOrder())
                .build()).toList();
    }

    @PostMapping("/bulk/edit")
    public SEResponse bulkEdit(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            log.info("/bulk/edit:: API started");
            BulkEditProductReqBean req = request.getGenericRequestDataObject(BulkEditProductReqBean.class);
            CommonUtils.extractHeaders(httpServletRequest, req);
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Permission.EDIT,
                    Activity.INVENTORY_MANAGEMENT);

            Role role = usersBean.getRole();
            UserType user_type = role.getUser_type();
            Seller seller;
            switch (user_type) {
                case SELLER:
                    seller = usersBean.getSeller();
                    break;
                case SUPER_ADMIN:
                    if (!StringUtils.hasText(req.getSeller_id())) {
                        throw new CustomIllegalArgumentsException(ResponseCode.PLEASE_SELECT_SELLER);
                    }
                    SEFilter filterS = new SEFilter(SEFilterType.AND);
                    filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getSeller_id()));
                    filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

                    seller = seller_Service.repoFindOne(filterS);
                    if (seller == null) {
                        throw new CustomIllegalArgumentsException(ResponseCode.SELLER_NOT_FOUND);
                    }
                    break;
                default:
                    throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }

            if (CollectionUtils.isEmpty(req.getProducts())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PRODUCTS);
            }
            req.getProducts().forEach(product -> {
                if (!StringUtils.hasText(product.getProduct_id())) {
                    throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PRODUCT_ID);
                }
            });
            List<String> productIds = req.getProducts().stream().map(ProductReqBean::getProduct_id).toList();
            this.bulkEdit(productIds, seller, req.getProducts(), usersBean);
            return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/product/bulk/edit:: error occurred:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    private void bulkEdit(List<String> productIds, Seller seller, List<ProductReqBean> req, UsersBean usersBean) {
        SEFilter filterP = new SEFilter(SEFilterType.AND);
        filterP.addClause(WhereClause.in(BaseMongoEntity.Fields.id, productIds));
        filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterP.addClause(WhereClause.eq(Products.Fields.seller_id, seller.getId()));

        List<Products> products = productService.repoFind(filterP);
        if (CollectionUtils.isEmpty(products)) {
            throw new CustomIllegalArgumentsException(ResponseCode.PRODUCT_NOT_FOUND);
        }
        if (products.size() != req.size()) {
            throw new CustomIllegalArgumentsException(ResponseCode.PRODUCT_NOT_FOUND);
        }

        List<Products> updatedProducts = new ArrayList<>();

        Map<String, Products> mapP = products.stream().collect(Collectors.toMap(Products::getId, product -> product));
        Map<String, Category_Master> mapC = this.getStringCategoryMasterMap(products.stream()
                .map(Products::getCategory_id).distinct().toList());

        req.forEach(product -> {
                    this.validateRequest(product, true);
                    Category_Master category_Master = mapC.get(product.getCategory_id());
                    Products products1 = mapP.get(product.getProduct_id());
                    Map<String, List<String>> mapSC = this.getSubCategoriesMap(category_Master, products1.getGroup_id());
                    List<SelectedSubCatagories> listSC = this.buildSelectedSubCategories(product, mapSC);
                    this.validateMandatorySubCategories(category_Master, listSC, product.getGroup_id());

                    BigDecimal mrp = new BigDecimal(product.getMrp());
                    BigDecimal sp = new BigDecimal(product.getSelling_price());
                    products1.setName(product.getName());
                    products1.setMrp(CommonUtils.rupeeToPaise(mrp));
                    products1.setSelling_price(CommonUtils.rupeeToPaise(sp));
                    products1.setSelected_sub_catagories(listSC);
                    products1.setCategory_id(category_Master.getId());
                    products1.setQuantity(Long.valueOf(product.getQuantity()));
                    products1.setDescription(StringUtils.hasText(product.getDescription()) ? product.getDescription() : null);
                    products1.setMedia(product.getMedia());
                    products1.setIs_secure(product.getIs_secure() != null && product.getIs_secure());
                    updatedProducts.add(products1);
                }
        );
        updatedProducts.forEach(product -> productService.update(product.getId(), product, usersBean.getId()));
    }

    @PostMapping("/edit")
    public SEResponse edit(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            log.info("/product/edit:: API started");
            ProductReqBean req = request.getGenericRequestDataObject(ProductReqBean.class);
            CommonUtils.extractHeaders(httpServletRequest, req);
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Permission.EDIT,
                    Activity.INVENTORY_MANAGEMENT);

            Role role = usersBean.getRole();
            UserType user_type = role.getUser_type();
            Seller seller;
            switch (user_type) {
                case SELLER:
                    seller = usersBean.getSeller();
                    break;
                case SUPER_ADMIN:
                    if (!StringUtils.hasText(req.getSeller_id())) {
                        throw new CustomIllegalArgumentsException(ResponseCode.PLEASE_SELECT_SELLER);
                    }
                    SEFilter filterS = new SEFilter(SEFilterType.AND);
                    filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getSeller_id()));
                    filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

                    seller = seller_Service.repoFindOne(filterS);
                    if (seller == null) {
                        throw new CustomIllegalArgumentsException(ResponseCode.SELLER_NOT_FOUND);
                    }
                    break;
                default:
                    throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }

            if (!StringUtils.hasText(req.getProduct_id())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PRODUCT_ID);
            }
            this.bulkEdit(List.of(req.getProduct_id()), seller, List.of(req), usersBean);
            return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/product/edit:: error occurred:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @GetMapping("/find")
    public SEResponse getFind(@RequestParam(required = false) MultiValueMap<String, List<List<String>>> filters, // Optional
                              // filters
                              @RequestParam(required = false) String name,
                              @RequestParam(defaultValue = "${se.default.page}") int page,
                              @RequestParam(defaultValue = "${se.default.size}") int size, HttpServletRequest httpServletRequest) {
        try {
            FindProductBean req = new FindProductBean();
            req.creatObj(filters, name, page, size);
            List<ProductDetailsBean> products = findProducts(httpServletRequest, req);
            return SEResponse.getBasicSuccessResponseList(products, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("getFind:: error occurred:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/find")
    public SEResponse find(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            FindProductBean req = request.getGenericRequestDataObject(FindProductBean.class);
            List<ProductDetailsBean> products = findProducts(httpServletRequest, req);
            return SEResponse.getBasicSuccessResponseList(products, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("find:: error occurred:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/report")
    public void report(@RequestBody SERequest request, HttpServletRequest httpServletRequest,
                       HttpServletResponse response) {
        try {
            FindProductBean req = request.getGenericRequestDataObject(FindProductBean.class);
            List<ProductDetailsBean> products = findProducts(httpServletRequest, req);

            // Generate and send the report
            ExcelGenerationUtility.generateExcelReport(products, ReportType.PRODUCT_DETAILED, response);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("product/report:: error occurred:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }


    @NotNull
    private static Map<String, FileGeneratorUtil.SheetConfig<?, ?>> getSheetConfigMap(
            List<OrderReportDTO> orders, List<OrderItemReportsDTO> orderItemList) {
        log.debug("getSheetConfigMap:: Creating sheet config for {} orders and {} order items",
                orders.size(), orderItemList.size());

        FileGeneratorUtil.SheetConfig<OrderReportDTO, OrderProperties> orderConfig =
                new FileGeneratorUtil.SheetConfig<>(orders, OrderProperties.class);
        FileGeneratorUtil.SheetConfig<OrderItemReportsDTO, OrderItemsProperties> orderItemsConfig =
                new FileGeneratorUtil.SheetConfig<>(orderItemList, OrderItemsProperties.class);

        Map<String, FileGeneratorUtil.SheetConfig<?, ?>> sheetConfigMap = new HashMap<>();
        sheetConfigMap.put("Orders", orderConfig);
        sheetConfigMap.put("Order Items", orderItemsConfig);
        return sheetConfigMap;
    }

    private List<ProductDetailsBean> findProducts(HttpServletRequest httpServletRequest, FindProductBean req)
            throws JsonProcessingException {
        CommonUtils.extractHeaders(httpServletRequest, req);
        UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.PRODUCTS,
                Activity.INVENTORY_MANAGEMENT);
        SEFilter filterSE = this.createFilterForProductList(req, usersBean);

        searchHistoryAsyncHelper.createSearchHistory(usersBean.getId(), usersBean.getRole().getUser_type_id(),
                filterSE);

        List<Products> listP = productService.repoFind(filterSE);
        if (CollectionUtils.isEmpty(listP)) {
            return Collections.emptyList();
        }

        return this.convertToBean(listP);
    }

    @PostMapping("/delete")
    public SEResponse delete(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {

            FindProductBean req = request.getGenericRequestDataObject(FindProductBean.class);
            CommonUtils.extractHeaders(httpServletRequest, req);
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Permission.EDIT,
                    Activity.INVENTORY_MANAGEMENT);

            Role role = usersBean.getRole();
            UserType user_type = role.getUser_type();
            if (!(user_type == UserType.SELLER || user_type == UserType.SUPER_ADMIN)) {
                throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }
            if (!StringUtils.hasText(req.getId())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_ENTITY);
            }
            SEFilter filterSE = new SEFilter(SEFilterType.AND);
            filterSE.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getId()));

            Products product = productService.repoFindOne(filterSE);
            if (product == null) {
                return SEResponse.getEmptySuccessResponse(ResponseCode.NO_RECORD);
            }
            if (product.isDeleted()) {
                return SEResponse.getEmptySuccessResponse(ResponseCode.ALREADY_DELETED);
            }
            productService.deleteOne(product.getId(), usersBean.getId());
            return SEResponse.getEmptySuccessResponse(ResponseCode.PRODUCT_DELETED);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("delete:: error occurred:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/findForEdit")
    public SEResponse findForEdit(@RequestBody SERequest request) {
        try {
            FindProductBean req = request.getGenericRequestDataObject(FindProductBean.class);
            if (!StringUtils.hasText(req.getId())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_ENTITY);
            }
            SEFilter filterSE = new SEFilter(SEFilterType.AND);
            filterSE.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getId()));
            filterSE.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Products product = productService.repoFindOne(filterSE);
            if (product == null) {
                return SEResponse.getEmptySuccessResponse(ResponseCode.NO_RECORD);
            }
            Category_Master category_Master = this.getCategoryMaster(product.getCategory_id());
            ProductDetailsBean productToBean = this.convertProductToBean(product, category_Master);
            return SEResponse.getBasicSuccessResponseObject(productToBean, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("findForEdit:: error occurred:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/findOne")
    public SEResponse findOne(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        try {
            FindProductBean req = request.getGenericRequestDataObject(FindProductBean.class);
            CommonUtils.extractHeaders(httpServletRequest, req);
            UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.PRODUCTS,
                    Activity.INVENTORY_MANAGEMENT);
            if (!StringUtils.hasText(req.getId())) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_ENTITY);
            }
            SEFilter filterSE = new SEFilter(SEFilterType.AND);
            filterSE.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getId()));
            filterSE.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Products product = productService.repoFindOne(filterSE);
            if (product == null) {
                return SEResponse.getEmptySuccessResponse(ResponseCode.NO_RECORD);
            }

            Category_Master category_Master = getCategoryMaster(product.getCategory_id());
            List<String> list_filterable = category_Master.getGroups().stream()
                    .flatMap(e -> e.getSub_categories().stream()).filter(SubCategory::isFilterable).map(SubCategory::getName)
                    .toList();
            Map<String, List<String>> relatedFilters = new HashMap<>();

            product.getSelected_sub_catagories().stream().filter(e -> list_filterable.contains(e.getSub_category()))
                    .forEach(s -> {
                        if (!relatedFilters.containsKey(s.getSub_category())) {
                            relatedFilters.put(s.getSub_category(), new ArrayList<>());
                        }
                        relatedFilters.get(s.getSub_category()).addAll(s.getSelected_attributes());
                    });

            this.makeValuesUnique(relatedFilters);

            if (CollectionUtils.isEmpty(relatedFilters)) {
                throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
            }
            SEFilter filterRI = new SEFilter(SEFilterType.AND);
            Map<String, Object> map = new HashMap<>();
            for (Entry<String, List<String>> entry : relatedFilters.entrySet()) {
                if (StringUtils.hasText(entry.getKey()) && !CollectionUtils.isEmpty(entry.getValue())) {
                    map.put(SelectedSubCatagories.Fields.sub_category, entry.getKey());
                    map.put(SelectedSubCatagories.Fields.selected_attributes, entry.getValue());
                }
            }
            if (!map.isEmpty()) {
                filterRI.addClause(WhereClause.elem_match(Products.Fields.selected_sub_catagories, map));
            }
            filterRI.addClause(WhereClause.eq("groups.group_id", product.getGroup_id()));
            filterRI.addClause(WhereClause.notEq(BaseMongoEntity.Fields.id, product.getId()));
            filterRI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            List<Products> listRI = productService.repoFind(filterRI);

            ProductDetailsBean resBean = this.productToBean(listRI, product);
            if (usersBean.getRole().getUser_type() == UserType.CUSTOMER
                    || usersBean.getRole().getUser_type() == UserType.GUEST) {
                SEFilter filterC = new SEFilter(SEFilterType.AND);
                filterC.addClause(WhereClause.eq(Cart.Fields.user_id, usersBean.getId()));
                filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

                Cart cart = cart_Service.repoFindOne(filterC);
                if (cart == null) {
                    cart = new Cart();
                    cart.setUser_id(usersBean.getId());
                    cart = cart_Service.create(cart, usersBean.getId());
                }
                List<Item> cart_items = cart.getCart_items();
                CartDetailsBuilder cartDetailsBuilder = CartDetails.builder();
                if (!CollectionUtils.isEmpty(cart_items)) {
                    Predicate<Item> p1 = item -> item.getProduct_id().equals(product.getId());
                    Predicate<Item> p2 = Item::is_secure;
                    Predicate<Item> p3 = item -> !item.is_secure();
                    Optional<Item> secureItem = cart_items.stream().filter(p1.and(p2)).findFirst();
                    if (secureItem.isPresent()) {
                        Item item = secureItem.get();
                        cartDetailsBuilder.secure_items(item.is_secure() ? item.getQuantity() : 0);
                    }
                    Optional<Item> normalItem = cart_items.stream().filter(p1.and(p3)).findFirst();
                    if (normalItem.isPresent()) {
                        Item item = normalItem.get();
                        cartDetailsBuilder.normal_items(item.is_secure() ? 0 : item.getQuantity());
                    }
                }
                resBean.setCart_info(cartDetailsBuilder.build());
            }
            return SEResponse.getBasicSuccessResponseObject(resBean, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("findOne:: error occurred:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    @PostMapping("/upload/image")
    public SEResponse uploadPhoto(@RequestParam("file") MultipartFile file, HttpServletRequest httpServletRequest) {
        try {
            String req_user_id = httpServletRequest.getHeader("req_user_id");
            String req_role_id = httpServletRequest.getHeader("req_role_id");
            if (!StringUtils.hasText(req_user_id) || !StringUtils.hasText(req_role_id)) {
                throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }
            UsersBean usersBean = users_Service.validateUserForActivity(req_user_id, Activity.INVENTORY_MANAGEMENT);
            File_Upload_Details file_Upload_Details = awsS3Service.uploadPhoto(file, usersBean,
                    DocumentType.PRODUCT_IMAGE);
            Media media = new Media();
            media.setCdn_url(file_Upload_Details.getFile_url());
            return SEResponse.getBasicSuccessResponseObject(media, ResponseCode.SUCCESSFUL);
        } catch (IOException e) {
            log.error("Exception occurred: message: {}", e.getMessage(), e);
            return SEResponse.getBadRequestFailureResponse(ResponseCode.ERR_0001);
        }
    }


    /* <<<<<<<<<<<<<<  ✨ Windsurf Command 🌟 >>>>>>>>>>>>>>>> */
    private SEFilter createFilterForProductList(FindProductBean req, UsersBean usersBean)
            throws JsonProcessingException {

        log.debug("createFilterForProductList:: req: {}", req);
        SEFilter filterSE = new SEFilter(SEFilterType.AND);
        switch (usersBean.getRole().getUser_type()) {
            case SELLER:
                filterSE.addClause(WhereClause.eq(Products.Fields.seller_id, usersBean.getRole().getSeller_id()));
                break;
            case CUSTOMER, GUEST:
                String nearest_seller_id;
                boolean storeOperational = false;
                String nearest_seller = usersBean.getNearestSeller();
                if (StringUtils.hasText(nearest_seller)) {
                    storeOperational = storeActivityService.isStoreOperational(nearest_seller);
                }
                if (StringUtils.hasText(usersBean.getNearestPincode()) && (!storeOperational || !StringUtils.hasText(nearest_seller))) {
                    NearestSellerRes nearestSeller = porterUtility.getNearestSeller(usersBean.getNearestPincode(), usersBean.getMobile_no(), usersBean.getFirst_name(), usersBean.getId());
                    nearest_seller_id = nearestSeller.getSeller_id();
                    if (!nearest_seller_id.equals(nearest_seller)) {
                        String user_id = usersBean.getId();
                        SEFilter filterU = new SEFilter(SEFilterType.AND);
                        filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, user_id));
                        filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

                        Users users = users_Service.repoFindOne(filterU);
                        users.setNearestSeller(nearest_seller_id);

                        users_Service.update(user_id, users, user_id);
                    }
                } else {
                    nearest_seller_id = defaultSeller;
                }
                if (StringUtils.hasText(nearest_seller_id)) {
                    filterSE.addClause(WhereClause.eq(Products.Fields.seller_id, nearest_seller_id));
                }
                break;

            default:
                break;
        }
        if (StringUtils.hasText(req.getName())) {
            filterSE.addClause(WhereClause.like(Products.Fields.name, req.getName()));
        }
        // TODO: 2 endpoints for store and customer
        if (StringUtils.hasText(req.getCategory_id())) {
            filterSE.addClause(WhereClause.eq(Products.Fields.category_id, req.getCategory_id()));
        }

        List<SubCategory> mappableSubCategories = null;
        if (req.getGroup_id() != null) {
//            if (!CollectionUtils.isEmpty(req.getFilters())) {
//                SEFilter filterG = new SEFilter(SEFilterType.AND);
//                filterG.addClause(WhereClause.eq("groups.group_id", req.getGroup_id()));
//                filterG.addClause(WhereClause.eq("groups.sub_categories.mappable", true));
//                filterG.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
//
//                Category_Master category_master = category_MasterService.repoFindOne(filterG);
//                if (category_master != null) {
//                    mappableSubCategories = category_master.getGroups().stream().flatMap(e -> e.getSub_categories().stream()).filter(SubCategory::isMappable).toList();
//                }
//            }
            filterSE.addClause(WhereClause.eq(Products.Fields.group_id, req.getGroup_id()));
        }
        if (!CollectionUtils.isEmpty(req.getFilters())) {
            for (Entry<String, List<String>> entry : req.getFilters().entrySet()) {
                if (StringUtils.hasText(entry.getKey()) && !CollectionUtils.isEmpty(entry.getValue())) {
                    Map<String, Object> map = new HashMap<>();
                    map.put(SelectedSubCatagories.Fields.sub_category, entry.getKey());
//                    if (mappableSubCategories != null) {
//                        for (SubCategory subCategory : mappableSubCategories) {
//                            if (subCategory.getName().equals(entry.getKey())) {
//                                Map<String, List<String>> mapping = subCategory.getMapping();
//                                List<String> attributes = new ArrayList<>();
//                                for (String attribute : entry.getValue()) {
//                                    attributes.addAll(mapping.getOrDefault(attribute, List.of(attribute)));
//                                }
//                                map.put(SelectedSubCatagories.Fields.selected_attributes, attributes);
//                            }
//                        }
//                    } else {
                    map.put(SelectedSubCatagories.Fields.selected_attributes, entry.getValue());
//                    }
                    filterSE.addClause(WhereClause.elem_match(Products.Fields.selected_sub_catagories, map));
                }
            }
        }
        filterSE.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        if (StringUtils.hasText(req.getSort_by())) {
            OrderBy sort = switch (req.getSort_by()) {
                case "price_low_to_high" -> new OrderBy(Products.Fields.selling_price, SortOrder.ASC);
                case "price_high_to_low" -> new OrderBy(Products.Fields.selling_price, SortOrder.DESC);
                case "newest" -> new OrderBy(BaseMongoEntity.Fields.creation_date, SortOrder.DESC);
                case "oldest" -> new OrderBy(BaseMongoEntity.Fields.creation_date, SortOrder.ASC);
                default -> new OrderBy(BaseMongoEntity.Fields.modification_date, SortOrder.DESC);
            };
            filterSE.setOrderBy(sort);
        }
        int page = req.getPage() < 0 ? defaultPage : req.getPage();
        int size = req.getSize() < 1 ? defaultSize : req.getSize();
        Pagination pagination = new Pagination(page, size);
//        filterSE.setPagination(pagination);
        log.debug("createFilterForProductList:: filterSE: {}", filterSE);
        return filterSE;
    }
    /* <<<<<<<<<<  5a954e3c-b605-4160-a4be-04fd8c3228dd  >>>>>>>>>>> */

    private void makeValuesUnique(Map<String, List<String>> map) {
        for (Entry<String, List<String>> entry : map.entrySet()) {
            List<String> uniqueList = new ArrayList<>(new HashSet<>(entry.getValue()));
            entry.setValue(uniqueList);
        }
    }

    private ProductDetailsBean productToBean(List<Products> relatedProducts, Products product) {
        ProductDetailsBean mainProductDetails = this.convertToBean(Collections.singletonList(product)).get(0);
        if (!CollectionUtils.isEmpty(relatedProducts)) {
            List<String> category_ids = new ArrayList<>();
            List<ProductDetailsBean> relatedProductDetailsList = new ArrayList<>();
            Map<String, Category_Master> mapCM = this.getCategoryMaster(relatedProducts, category_ids);
            for (Products temp : relatedProducts) {
                Category_Master category_Master = mapCM.get(temp.getCategory_id());
                relatedProductDetailsList.add(this.convertProductToBean(temp, category_Master));
            }
            mainProductDetails.setRelated_products(relatedProductDetailsList);
        }

        return mainProductDetails;
    }

    private List<ProductDetailsBean> convertToBean(List<Products> products) {

        List<ProductDetailsBean> productDetailsList = new ArrayList<>();
        List<String> category_ids = new ArrayList<>();
//        if (variantMap != null && !variantMap.isEmpty()) {
//            Collection<List<Products>> values = variantMap.values();
//            category_ids.addAll(values.stream().flatMap(Collection::stream).map(Products::getCategory_id).distinct().toList());
//        }
        Map<String, Category_Master> mapCM = this.getCategoryMaster(products, category_ids);
        for (Products product : products) {
            Category_Master category_Master = mapCM.get(product.getCategory_id());
            ProductDetailsBean productDetailsBean = this.convertProductToBean(product, category_Master);
//            if (StringUtils.hasText(product.getVarient_mapping_id()) && !CollectionUtils.isEmpty(variantMap)
//                    && variantMap.containsKey(product.getVarient_mapping_id())) {
//                List<Products> variants = variantMap.get(product.getVarient_mapping_id());
//                for (Products variant : variants) {
//                    if (!variant.getId().equals(product.getId())) {
//                        Category_Master cm = mapCM.get(product.getCategory_id());
//                        ProductDetailsBean variantBean = this.convertProductToBean(variant, cm);
//                        if (CollectionUtils.isEmpty(productDetailsBean.getVarients())) {
//                            productDetailsBean.setVarients(new ArrayList<>());
//                        }
//                        productDetailsBean.getVarients().add(variantBean);
//                    }
//                }
//            }
            productDetailsList.add(productDetailsBean);
        }
        return productDetailsList;
    }

    private Map<String, Category_Master> getCategoryMaster(List<Products> products, List<String> category_ids) {
        category_ids.addAll(products.stream().map(Products::getCategory_id).distinct().toList());
        category_ids.remove(null);
        Map<String, Category_Master> mapCM = new HashMap<>();
        if (!CollectionUtils.isEmpty(category_ids)) {
            SEFilter filterCM = new SEFilter(SEFilterType.AND);
            filterCM.addClause(WhereClause.in(BaseMongoEntity.Fields.id, category_ids));
            filterCM.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            List<Category_Master> listCM = category_MasterService.repoFind(filterCM);
            if (!CollectionUtils.isEmpty(listCM)) {
                mapCM.putAll(listCM.stream().collect(Collectors.toMap(BaseMongoEntity::getId, e -> e)));
            }
        }
        return mapCM;
    }

    private ProductDetailsBean convertProductToBean(Products product,
                                                    Category_Master category_Master) {
        ProductDetailsBean bean = new ProductDetailsBean();
        bean.setName(product.getName());
        bean.setId(product.getId());
        bean.setProduct_code(product.getProduct_code());
        bean.setSelling_price(CommonUtils.paiseToRupee(product.getSelling_price()));
        bean.setMrp(CommonUtils.paiseToRupee(product.getMrp()));
        bean.setSelected_sub_catagories(product.getSelected_sub_catagories());
        bean.setQuantity(product.getQuantity().intValue());
        bean.setDescription(product.getDescription());
        bean.setCategory_id(category_Master.getId());
        bean.setCategory_name(category_Master.getName());
        bean.setSecure(Boolean.TRUE.equals(product.getIs_secure()));
        bean.setMedia(product.getMedia());
        bean.setGroup_id(product.getGroup_id());
        return bean;
    }

    private Map<String, List<Products>> getVariants(Products... products) {
        List<Products> listP = Arrays.asList(products);
        return this.getVariants(listP);
    }

    private Map<String, List<Products>> getVariants(List<Products> listP) {
        Set<String> variantMappingIds = listP.stream().map(Products::getVarient_mapping_id).collect(Collectors.toSet());
        variantMappingIds.remove(null);
        if (CollectionUtils.isEmpty(variantMappingIds)) {
            return new HashMap<>();
        }
        List<String> product_ids = listP.stream().map(Products::getId).toList();
        SEFilter filterVM = new SEFilter(SEFilterType.AND);
        filterVM.addClause(WhereClause.in(BaseMongoEntity.Fields.id, CommonUtils.convertS2L(variantMappingIds)));
        filterVM.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Varient_Mapping> listVM = varient_Mapping_Service.repoFind(filterVM);
        if (CollectionUtils.isEmpty(listVM)) {
            return new HashMap<>();
        }
        List<String> variant_ids = listVM.stream().map(Varient_Mapping::getId).toList();
        SEFilter filterVariants = new SEFilter(SEFilterType.AND);
        filterVariants.addClause(WhereClause.in(Products.Fields.varient_mapping_id, variant_ids));
        filterVariants.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterVariants.addClause(WhereClause.nin(BaseMongoEntity.Fields.id, product_ids));

        List<Products> varients = productService.repoFind(filterVariants);
        if (CollectionUtils.isEmpty(varients)) {
            return new HashMap<>();
        }
        Map<String, List<Products>> mapV = new HashMap<>();
        varients.forEach(e -> {
            if (!mapV.containsKey(e.getVarient_mapping_id())) {
                mapV.put(e.getVarient_mapping_id(), new ArrayList<>());
            }
            mapV.get(e.getVarient_mapping_id()).add(e);
        });
        return mapV;
    }

    private void validateRequest(ProductReqBean req, boolean isEdit) {
        if (!StringUtils.hasText(req.getCategory_id())) {
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_CATEGORY);
        }
        if (!StringUtils.hasText(req.getName())) {
            throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PRODUCT_NAME);
        }
        if (!isEdit && (req.getGroup_id() == null || req.getGroup_id() <= 0)) {
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_GROUP);
        }
//		if (!SERegExpUtils.standardTextValidation(req.getName())) {
//			throw new CustomIllegalArgumentsException(ResponseCode.INVALID_PRODUCT_NAME);
//		}
        if (CollectionUtils.isEmpty(req.getSub_categories())) {
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SUB_CATEGORY);
        }
        if (StringUtils.hasText(req.getDescription())) {
//            if (!SERegExpUtils.standardTextValidation(req.getDescription())) {
//                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_PRODUCT_DESCRIPTION);
//            }
        }
        if (!StringUtils.hasText(req.getQuantity())) {
            throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PRODUCT_QUANTITY);
        }
        if (!SERegExpUtils.isQuantity(req.getQuantity())) {
            throw new CustomIllegalArgumentsException(ResponseCode.INVAALID_PRODUCT_QUANTITY);
        }
        if (!StringUtils.hasText(req.getMrp())) {
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_MRP);
        }
        if (!SERegExpUtils.isPriceInDecimal(req.getMrp())) {
            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_PRODUCT_MRP);
        }
        if (!StringUtils.hasText(req.getSelling_price())) {
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SP);
        }
        if (!SERegExpUtils.isPriceInDecimal(req.getMrp())) {
            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SELLING_PRICE);
        }
        BigDecimal mrp = new BigDecimal(req.getMrp());
        BigDecimal sp = new BigDecimal(req.getSelling_price());
        if (sp.compareTo(mrp) > 0) {
            throw new CustomIllegalArgumentsException(ResponseCode.SP_MAX_MRP);
        }
//        if (StringUtils.hasText(req.getDescription()) && !SERegExpUtils.standardTextValidation(req.getDescription())) {
//            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_PRODUCT_DESCRIPTION);
//        }
    }

    private void validateRequestForBulk(List<ProductReqBean> list) {
        for (ProductReqBean req : list) {
            this.validateRequest(req, false);
        }
    }

    private Category_Master getCategoryMaster(String categoryId) {
        // Clear cache if expired
//        if (System.currentTimeMillis() - lastCacheClearTime > CACHE_EXPIRY_TIME) {
//            categoryMasterCache.clear();
//            lastCacheClearTime = System.currentTimeMillis();
//        }

        // Try to get from cache first
//        Category_Master cached = categoryMasterCache.get(categoryId);
//        if (cached != null) {
//            return cached;
//        }

        SEFilter filterC = new SEFilter(SEFilterType.AND);
        filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, categoryId));
        filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Category_Master categoryMaster = category_MasterService.repoFindOne(filterC);
        if (categoryMaster == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.CATEGORY_NOT_FOUND);
        }

        return categoryMaster;
    }

    private Map<String, List<String>> getSubCategoriesMap(Category_Master categoryMaster, Integer groupId) {
        if (CollectionUtils.isEmpty(categoryMaster.getGroups())) {
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
        Map<Integer, List<SubCategory>> sub_categories = categoryMaster.getSub_categories_by_group();
        if (CollectionUtils.isEmpty(sub_categories)) {
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }

        if (!sub_categories.containsKey(groupId)) {
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }

        return sub_categories.get(groupId).stream().collect(Collectors.toMap(SubCategory::getName,
                e -> CollectionUtils.isEmpty(e.getAttributes()) ? new ArrayList<>() : e.getAttributes()));
    }

    private List<SelectedSubCatagories> buildSelectedSubCategories(ProductReqBean req,
                                                                   Map<String, List<String>> mapSC) {
        List<SelectedSubCatagories> listSC = new ArrayList<>();
        req.getSub_categories().forEach((key, val) -> {
            if (!StringUtils.hasText(key)) {
                throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SUB_CATEGORY);
            }
            if (!CollectionUtils.isEmpty(val)) {
                val = val.stream().filter(e -> e != null && !e.isEmpty()).toList();
            }
            if (CollectionUtils.isEmpty(val)) {
                return;
            }
            if (!mapSC.containsKey(key)) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SUB_CATEGORY);
            }

            List<String> attributes = mapSC.get(key);
            if (!CollectionUtils.isEmpty(attributes) && val.stream().anyMatch(x -> !attributes.contains(x))) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SUB_CATEGORY);
            }

            SelectedSubCatagories subCategories = new SelectedSubCatagories();
            subCategories.setSub_category(key);
            subCategories.setSelected_attributes(val);
            listSC.add(subCategories);
        });
        return listSC;
    }

    private void validateMandatorySubCategories(Category_Master categoryMaster, List<SelectedSubCatagories> listSC, Integer groupId) {
        Map<String, List<String>> mapSC = listSC.stream()
                .collect(Collectors.toMap(SelectedSubCatagories::getSub_category, SelectedSubCatagories::getSelected_attributes));
        List<SubCategory> sub_categories = categoryMaster.getSub_categories_by_group().getOrDefault(groupId, new ArrayList<>());
        List<SubCategory> sorted = sub_categories.stream()
                .sorted(Comparator.comparingInt(SubCategory::getOrder))
                .toList();
        sorted.forEach(e -> {
            if (e.isMandate() && (!mapSC.containsKey(e.getName()) || CollectionUtils.isEmpty(mapSC.get(e.getName())))) {
                throw new CustomIllegalArgumentsException(e.getName() + " is mandatory.");
            }
        });
    }

    private String upsertAndGetVariantMappingId(ProductReqBean req, UsersBean usersBean, Role role) {
        if (StringUtils.hasText(req.getVarient_mapping_id())) {
            SEFilter filterV = new SEFilter(SEFilterType.AND);
            filterV.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            filterV.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getVarient_mapping_id()));

            Varient_Mapping varient_Mapping = varient_Mapping_Service.repoFindOne(filterV);
            if (varient_Mapping == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.VARIENT_NOT_FOUND);
            }
            return varient_Mapping.getId();
        } else if (StringUtils.hasText(req.getVarient_name())) {
            if (!SERegExpUtils.standardTextValidation(req.getVarient_name())) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_VARIENT_NAME);
            }

            String variant_name = CommonUtils.toTitleCase(req.getVarient_name());

            SEFilter filterV = new SEFilter(SEFilterType.AND);
            filterV.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            filterV.addClause(WhereClause.eq(Varient_Mapping.Fields.name, variant_name));
            if (role.getUser_type() == UserType.SELLER) {
                filterV.addClause(WhereClause.eq(Varient_Mapping.Fields.seller_id, usersBean.getRole().getSeller_id()));
            }
            long count = varient_Mapping_Service.countByFilter(filterV);
            if (count > 0) {
                throw new CustomIllegalArgumentsException(ResponseCode.DUPLICATE_VARIENT);
            }

            Varient_Mapping variant = new Varient_Mapping();
            variant.setName(variant_name);
            if (role.getUser_type() == UserType.SELLER) {
                variant.setSeller_id(role.getSeller_id());
                variant.setSeller_code(role.getSeller_code());
            }

            variant = varient_Mapping_Service.create(variant, usersBean.getId());
            return variant.getId();
        }
        return null;
    }


}
