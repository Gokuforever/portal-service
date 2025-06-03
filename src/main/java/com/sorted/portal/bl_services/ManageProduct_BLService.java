package com.sorted.portal.bl_services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sorted.commons.beans.*;
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.mongo.Category_Master.SubCategory;
import com.sorted.commons.entity.service.*;
import com.sorted.commons.enums.*;
import com.sorted.commons.enums.All_Status.Seller_Status;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.*;
import com.sorted.commons.helper.Pagination;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.helper.SearchHistoryAsyncHelper;
import com.sorted.commons.utils.*;
import com.sorted.portal.assisting.beans.ProductDetailsBean;
import com.sorted.portal.assisting.beans.ProductDetailsBean.CartDetails;
import com.sorted.portal.assisting.beans.ProductDetailsBean.CartDetails.CartDetailsBuilder;
import com.sorted.portal.enums.OrderItemsProperties;
import com.sorted.portal.enums.OrderProperties;
import com.sorted.portal.enums.ReportType;
import com.sorted.portal.request.beans.FindProductBean;
import com.sorted.portal.response.beans.OrderItemReportsDTO;
import com.sorted.portal.response.beans.OrderReportDTO;
import com.sorted.portal.service.ExcelGenerationUtility;
import com.sorted.portal.service.FileGeneratorUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/product")
public class ManageProduct_BLService {

    private final ProductService productService;
    private final Cart_Service cart_Service;
    private final Varient_Mapping_Service varient_Mapping_Service;
    private final Category_MasterService category_MasterService;
    private final Users_Service users_Service;
    private final Seller_Service seller_Service;
    private final File_Upload_Details_Service file_Upload_Details_Service;
    private final SearchHistoryAsyncHelper searchHistoryAsyncHelper;
    private final PorterUtility porterUtility;
    private final AwsS3Service awsS3Service;
    private final int defaultPage;
    private final int defaultSize;
    private final Map<String, Category_Master> categoryMasterCache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_TIME = 30 * 60 * 1000; // 30 minutes
    private long lastCacheClearTime = System.currentTimeMillis();

    public ManageProduct_BLService(ProductService productService, Cart_Service cart_Service,
                                   Varient_Mapping_Service varient_Mapping_Service, Category_MasterService category_MasterService,
                                   Users_Service users_Service, Seller_Service seller_Service, File_Upload_Details_Service file_Upload_Details_Service,
                                   SearchHistoryAsyncHelper searchHistoryAsyncHelper, PorterUtility porterUtility, AwsS3Service awsS3Service,
                                   @Value("${se.default.page}") int defaultPage, @Value("${se.default.size}") int defaultSize) {
        this.productService = productService;
        this.cart_Service = cart_Service;
        this.varient_Mapping_Service = varient_Mapping_Service;
        this.category_MasterService = category_MasterService;
        this.users_Service = users_Service;
        this.seller_Service = seller_Service;
        this.file_Upload_Details_Service = file_Upload_Details_Service;
        this.searchHistoryAsyncHelper = searchHistoryAsyncHelper;
        this.porterUtility = porterUtility;
        this.awsS3Service = awsS3Service;
        this.defaultPage = defaultPage;
        this.defaultSize = defaultSize;
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
            Category_Master category_Master = this.getCategoryMaster(req.getCategory_id());

            Map<String, List<String>> mapSC = this.getSubCategoriesMap(category_Master);

            Map<String, Media> mapMedia = products.stream().flatMap(e -> e.getMedia().stream())
                    .filter(a -> StringUtils.hasText(a.getDocument_id()))
                    .collect(Collectors.toMap(Media::getDocument_id, e -> e));
            if (!CollectionUtils.isEmpty(mapMedia)) {
                Set<String> document_ids = mapMedia.keySet();
                document_ids.remove(null);
                if (CollectionUtils.isEmpty(document_ids)) {
                    throw new CustomIllegalArgumentsException(ResponseCode.DOC_IDS_MISSING);
                }
                SEFilter filterFUD = new SEFilter(SEFilterType.AND);
                filterFUD.addClause(WhereClause.in(BaseMongoEntity.Fields.id, CommonUtils.convertS2L(document_ids)));
                filterFUD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
                filterFUD.addClause(WhereClause.eq(File_Upload_Details.Fields.document_type_id,
                        DocumentType.PRODUCT_IMAGE.getId()));
                if (user_type == UserType.SELLER) {
                    filterFUD.addClause(WhereClause.eq(File_Upload_Details.Fields.user_type, user_type.name()));
                    filterFUD.addClause(
                            WhereClause.eq(File_Upload_Details.Fields.entity_id, usersBean.getSeller().getId()));
                }

                List<File_Upload_Details> repoFind = file_Upload_Details_Service.repoFind(filterFUD);
                if (CollectionUtils.isEmpty(repoFind)) {
                    throw new CustomIllegalArgumentsException(ResponseCode.IMAGES_NOT_FOUND);
                }
                Set<String> db_document_ids = repoFind.stream().map(BaseMongoEntity::getId).collect(Collectors.toSet());
                if (!db_document_ids.containsAll(document_ids)) {
                    throw new CustomIllegalArgumentsException(ResponseCode.IMAGES_NOT_FOUND);
                }
            }

            List<Products> listP = products.parallelStream()
                    .map(productReqBean -> {
                        List<SelectedSubCatagories> listSC = this.buildSelectedSubCategories(productReqBean, mapSC);
                        this.validateMandatorySubCategories(category_Master, listSC);
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
                        product.setDescription(
                                StringUtils.hasText(productReqBean.getDescription()) ? productReqBean.getDescription() : null);

                        if (!CollectionUtils.isEmpty(productReqBean.getMedia())) {
                            List<Media> listMedia = productReqBean.getMedia().stream()
                                    .filter(e -> StringUtils.hasText(e.getDocument_id())).toList();
                            product.setMedia(listMedia);
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

            Map<String, List<String>> mapSC = this.getSubCategoriesMap(category_Master);

            List<SelectedSubCatagories> listSC = this.buildSelectedSubCategories(req, mapSC);

            this.validateMandatorySubCategories(category_Master, listSC);

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
            product.setDescription(StringUtils.hasText(req.getDescription()) ? req.getDescription() : null);
            if (!CollectionUtils.isEmpty(req.getMedia())) {
                Set<String> document_ids = req.getMedia().stream().map(Media::getDocument_id)
                        .filter(StringUtils::hasText).collect(Collectors.toSet());
                if (CollectionUtils.isEmpty(document_ids)) {
                    throw new CustomIllegalArgumentsException(ResponseCode.DOC_IDS_MISSING);
                }
                document_ids.remove(null);
                SEFilter filterFUD = new SEFilter(SEFilterType.AND);
                filterFUD.addClause(WhereClause.in(BaseMongoEntity.Fields.id, CommonUtils.convertS2L(document_ids)));
                filterFUD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
                filterFUD.addClause(WhereClause.eq(File_Upload_Details.Fields.document_type_id,
                        DocumentType.PRODUCT_IMAGE.getId()));
                if (user_type == UserType.SELLER) {
                    filterFUD.addClause(WhereClause.eq(File_Upload_Details.Fields.user_type, user_type.name()));
                    filterFUD.addClause(
                            WhereClause.eq(File_Upload_Details.Fields.entity_id, usersBean.getSeller().getId()));
                }

                List<File_Upload_Details> repoFind = file_Upload_Details_Service.repoFind(filterFUD);
                if (CollectionUtils.isEmpty(repoFind)) {
                    throw new CustomIllegalArgumentsException(ResponseCode.IMAGES_NOT_FOUND);
                }
                Set<String> db_document_ids = repoFind.stream().map(BaseMongoEntity::getId).collect(Collectors.toSet());
                if (!db_document_ids.containsAll(document_ids)) {
                    throw new CustomIllegalArgumentsException(ResponseCode.IMAGES_NOT_FOUND);
                }
                List<Media> listMedia = req.getMedia().stream().filter(e -> StringUtils.hasText(e.getDocument_id()))
                        .toList();
                product.setMedia(listMedia);
            }

            Products create = productService.create(product, usersBean.getId());
            return SEResponse.getBasicSuccessResponseObject(create, ResponseCode.SUCCESSFUL);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/product/create:: error occurred:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
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

            SEFilter filterP = new SEFilter(SEFilterType.AND);
            filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getProduct_id()));
            filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            filterP.addClause(WhereClause.eq(Products.Fields.seller_id, seller.getId()));

            Products product = productService.repoFindOne(filterP);
            if (product == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
            }

            this.validateRequest(req, false);

            Category_Master category_Master = this.getCategoryMaster(req.getCategory_id());

            Map<String, List<String>> mapSC = this.getSubCategoriesMap(category_Master);

            List<SelectedSubCatagories> listSC = this.buildSelectedSubCategories(req, mapSC);

            this.validateMandatorySubCategories(category_Master, listSC);

            String varient_mapping_id = this.upsertAndGetVariantMappingId(req, usersBean, role);

            BigDecimal mrp = new BigDecimal(req.getMrp());
            BigDecimal sp = new BigDecimal(req.getSelling_price());
            product.setName(req.getName());
            product.setMrp(CommonUtils.rupeeToPaise(mrp));
            product.setSelling_price(CommonUtils.rupeeToPaise(sp));
            product.setSelected_sub_catagories(listSC);
            product.setCategory_id(category_Master.getId());
            product.setQuantity(Long.valueOf(req.getQuantity()));
            product.setVarient_mapping_id(varient_mapping_id);
            product.setDescription(StringUtils.hasText(req.getDescription()) ? req.getDescription() : null);
            if (CollectionUtils.isEmpty(req.getMedia())) {
//				SEFilter filterFUD = new SEFilter(SEFilterType.AND);
//				filterFUD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
//				filterFUD.addClause(WhereClause.eq(File_Upload_Details.Fields.document_type_id,
//						DocumentType.PRODUCT_IMAGE.getId()));
//				if (user_type == UserType.SELLER) {
//					filterFUD.addClause(WhereClause.eq(File_Upload_Details.Fields.user_type, user_type.name()));
//					filterFUD.addClause(
//							WhereClause.eq(File_Upload_Details.Fields.entity_id, usersBean.getSeller().getId()));
//				}
//
//				List<File_Upload_Details> repoFind = file_Upload_Details_Service.repoFind(filterFUD);
//				if (!CollectionUtils.isEmpty(repoFind)) {
//					for (File_Upload_Details file_Upload_Details : repoFind) {
//						file_Upload_Details_Service.deleteOne(file_Upload_Details.getId(), usersBean.getId());
//					}
//				}
                product.setMedia(new ArrayList<>());
            } else {
                Set<String> document_ids = req.getMedia().stream().map(Media::getDocument_id)
                        .filter(StringUtils::hasText).collect(Collectors.toSet());
                if (CollectionUtils.isEmpty(document_ids)) {
                    throw new CustomIllegalArgumentsException(ResponseCode.DOC_IDS_MISSING);
                }
                SEFilter filterFUD = new SEFilter(SEFilterType.AND);
                filterFUD.addClause(WhereClause.in(BaseMongoEntity.Fields.id, CommonUtils.convertS2L(document_ids)));
                filterFUD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
                filterFUD.addClause(WhereClause.eq(File_Upload_Details.Fields.document_type_id,
                        DocumentType.PRODUCT_IMAGE.getId()));
                if (user_type == UserType.SELLER) {
                    filterFUD.addClause(WhereClause.eq(File_Upload_Details.Fields.user_type, user_type.name()));
                    filterFUD.addClause(
                            WhereClause.eq(File_Upload_Details.Fields.entity_id, usersBean.getSeller().getId()));
                }

                List<File_Upload_Details> repoFind = file_Upload_Details_Service.repoFind(filterFUD);
                if (CollectionUtils.isEmpty(repoFind)) {
                    throw new CustomIllegalArgumentsException(ResponseCode.IMAGES_NOT_FOUND);
                }
                Set<String> db_document_ids = repoFind.stream().map(BaseMongoEntity::getId).collect(Collectors.toSet());
                if (!db_document_ids.containsAll(document_ids)) {
                    throw new CustomIllegalArgumentsException(ResponseCode.IMAGES_NOT_FOUND);
                }
                List<Media> listMedia = req.getMedia().stream().filter(e -> StringUtils.hasText(e.getDocument_id()))
                        .toList();
                product.setMedia(listMedia);
            }

            product = productService.update(product.getId(), product, usersBean.getId());
            return SEResponse.getBasicSuccessResponseObject(product, ResponseCode.SUCCESSFUL);
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

        Map<String, String> mapImg = this.filterAndFetchImgMap(listP);

        return this.convertToBean(null, listP, mapImg);
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
            Map<String, String> mapImg = this.filterAndFetchImgMap(List.of(product));
            Category_Master category_Master = this.getCategoryMaster(product.getCategory_id());
            ProductDetailsBean productToBean = this.convertProductToBean(product, mapImg, category_Master);
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
            List<SEFilterNode> nodes = new ArrayList<>();
            for (Entry<String, List<String>> entry : relatedFilters.entrySet()) {
                SEFilterNode node = new SEFilterNode(SEFilterType.OR);
                if (StringUtils.hasText(entry.getKey()) && !CollectionUtils.isEmpty(entry.getValue())) {
                    Map<String, Object> map = new HashMap<>();
                    map.put(SelectedSubCatagories.Fields.sub_category, entry.getKey());
                    map.put(SelectedSubCatagories.Fields.selected_attributes, entry.getValue());
                    node.addClause(WhereClause.elem_match(Products.Fields.selected_sub_catagories, map));
                    nodes.add(node);
                }
            }
            filterRI.addNodes(nodes);
            filterRI.addClause(WhereClause.notEq(BaseMongoEntity.Fields.id, product.getId()));
            filterRI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            List<Products> listRI = productService.repoFind(filterRI);

            Map<String, List<Products>> mapV = this.getVariants(product);
//			Set<String> seller_ids = this.getSellerByPincode(req.getPincode());
            Map<String, String> mapImg = this.filterAndFetchImgMap(List.of(product));

            ProductDetailsBean resBean = this.productToBean(listRI, mapV, product, mapImg);
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
            media.setKey(file_Upload_Details.getDocument_id());
            media.setDocument_id(file_Upload_Details.getId());
            return SEResponse.getBasicSuccessResponseObject(media, ResponseCode.SUCCESSFUL);
        } catch (IOException e) {
            log.error("Exception occurred: message: {}", e.getMessage(), e);
            return SEResponse.getBadRequestFailureResponse(ResponseCode.ERR_0001);
        }
    }

    private Map<String, String> filterAndFetchImgMap(List<Products> listP) {
        // Extract image document IDs from products
        Set<String> imageIds = listP.stream()
                .filter(products -> products.getMedia() != null && !products.getMedia().isEmpty())
                .flatMap(products -> products.getMedia().stream().map(Media::getDocument_id))
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        if (CollectionUtils.isEmpty(imageIds)) {
            return new HashMap<>();
        }

        // Batch process file upload details
        int batchSize = 100; // Process in batches of 100
        Map<String, String> mapFUD = new HashMap<>();

        List<String> imageIdList = new ArrayList<>(imageIds);
        for (int i = 0; i < imageIdList.size(); i += batchSize) {
            List<String> batch = imageIdList.subList(i, Math.min(i + batchSize, imageIdList.size()));

            SEFilter filterFUD = new SEFilter(SEFilterType.AND);
            filterFUD.addClause(WhereClause.in(BaseMongoEntity.Fields.id, batch));
            filterFUD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            List<File_Upload_Details> listFUD = file_Upload_Details_Service.repoFind(filterFUD);
            if (!CollectionUtils.isEmpty(listFUD)) {
                listFUD.forEach(fud -> mapFUD.put(fud.getId(), fud.getDocument_id()));
            }
        }

        // Map the image IDs that have corresponding entries in mapFUD
        return imageIds.stream()
                .filter(mapFUD::containsKey)
                .collect(Collectors.toMap(id -> id, mapFUD::get));
    }

    private SEFilter createFilterForProductList(FindProductBean req, UsersBean usersBean)
            throws JsonProcessingException {

        SEFilter filterSE = new SEFilter(SEFilterType.AND);
        switch (usersBean.getRole().getUser_type()) {
            case SELLER:
                filterSE.addClause(WhereClause.eq(Products.Fields.seller_id, usersBean.getRole().getSeller_id()));
                filterSE.addClause(WhereClause.eq(Products.Fields.seller_code, usersBean.getRole().getSeller_code()));
                break;
            case CUSTOMER:
                String nearest_seller_id;
                Map<String, String> properties = usersBean.getProperties();
                if (CollectionUtils.isEmpty(properties) || !properties.containsKey("nearest_pincode")) {
                    throw new CustomIllegalArgumentsException(ResponseCode.SELECT_PINCODE);
                }
                String nearest_seller = properties.getOrDefault("nearest_seller", null);
                if (nearest_seller == null) {
                    NearestSellerRes nearestSeller = porterUtility.getNearestSeller(properties.get("nearest_pincode"), usersBean.getMobile_no(), usersBean.getFirst_name(), usersBean.getId());
                    nearest_seller_id = nearestSeller.getSeller_id();
                    properties.put("nearest_seller", nearest_seller_id);
                    String user_id = usersBean.getId();
                    SEFilter filterU = new SEFilter(SEFilterType.AND);
                    filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, user_id));
                    filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

                    Users users = users_Service.repoFindOne(filterU);
                    users.setProperties(properties);

                    users_Service.update(user_id, users, user_id);
                } else {
                    SEFilter filterS = new SEFilter(SEFilterType.AND);
                    filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
                    filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, nearest_seller));
                    filterS.addClause(WhereClause.eq(Seller.Fields.status, Seller_Status.ACTIVE.name()));

                    Seller seller = seller_Service.repoFindOne(filterS);
                    if (seller == null) {
                        NearestSellerRes nearestSeller = porterUtility.getNearestSeller(properties.get("nearest_pincode"), usersBean.getMobile_no(), usersBean.getFirst_name(), usersBean.getId());
                        nearest_seller_id = nearestSeller.getSeller_id();
                    } else {
                        nearest_seller_id = seller.getId();
                    }
                }
                filterSE.addClause(WhereClause.eq(Products.Fields.seller_id, nearest_seller_id));
                break;
            default:
//			SEFilter filterS = new SEFilter(SEFilterType.AND);
//			filterS.addClause(WhereClause.notEq(Seller.Fields.status, Seller_Status.ACTIVE.name()));
//			filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
//			List<Seller> sellers = seller_Service.repoFind(filterS);
//			if (!CollectionUtils.isEmpty(sellers)) {
//				List<String> ids = sellers.parallelStream().map(Seller::getId).toList();
//				filterSE.addClause(WhereClause.nin(Products.Fields.seller_id, ids));
//			}
                break;
        }
        if (StringUtils.hasText(req.getName())) {
            filterSE.addClause(WhereClause.like(Products.Fields.name, req.getName()));
        }
        if (!CollectionUtils.isEmpty(req.getFilters())) {
            for (Entry<String, List<String>> entry : req.getFilters().entrySet()) {
                if (StringUtils.hasText(entry.getKey()) && !CollectionUtils.isEmpty(entry.getValue())) {
                    Map<String, Object> map = new HashMap<>();
                    map.put(SelectedSubCatagories.Fields.sub_category, entry.getKey());
                    map.put(SelectedSubCatagories.Fields.selected_attributes, entry.getValue());
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
        filterSE.setPagination(pagination);
        return filterSE;
    }

//	private Set<String> getSellerByPincode(String pincode) {
//		Set<String> seller_ids = new HashSet<>();
//		if (StringUtils.hasText(pincode)) {
//			if (!SERegExpUtils.isPincode(pincode)) {
//				throw new CustomIllegalArgumentsException(ResponseCode.INVALID_PINCODE);
//			}
//			SEFilter filterS = new SEFilter(SEFilterType.AND);
//			filterS.addClause(WhereClause.eq(Seller.Fields.serviceable_pincodes, pincode));
//			filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
//
//			List<Seller> sellers = seller_Service.repoFind(filterS);
//			if (!CollectionUtils.isEmpty(sellers)) {
//				seller_ids = sellers.stream().map(e -> e.getId()).collect(Collectors.toSet());
//			}
//		}
//		return seller_ids;
//	}

    private void makeValuesUnique(Map<String, List<String>> map) {
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            List<String> uniqueList = new ArrayList<>(new HashSet<>(entry.getValue()));
            entry.setValue(uniqueList);
        }
    }

    private ProductDetailsBean productToBean(List<Products> relatedProducts, Map<String, List<Products>> variantMap,
                                             Products product, Map<String, String> mapImg) {
        ProductDetailsBean mainProductDetails = this.convertToBean(variantMap, Collections.singletonList(product), mapImg).get(0);
        if (!CollectionUtils.isEmpty(relatedProducts)) {
            List<String> category_ids = new ArrayList<>();
            List<ProductDetailsBean> relatedProductDetailsList = new ArrayList<>();
            Map<String, Category_Master> mapCM = this.getCategoryMaster(relatedProducts, category_ids);
            for (Products temp : relatedProducts) {
                Category_Master category_Master = mapCM.get(temp.getCategory_id());
                relatedProductDetailsList.add(this.convertProductToBean(temp, mapImg, category_Master));
            }
            mainProductDetails.setRelated_products(relatedProductDetailsList);
        }

        return mainProductDetails;
    }

    private List<ProductDetailsBean> convertToBean(Map<String, List<Products>> variantMap, List<Products> products,
                                                   Map<String, String> mapImg) {

        List<ProductDetailsBean> productDetailsList = new ArrayList<>();
        List<String> category_ids = new ArrayList<>();
        if (variantMap != null && !variantMap.isEmpty()) {
            Collection<List<Products>> values = variantMap.values();
            category_ids.addAll(values.stream().flatMap(Collection::stream).map(Products::getCategory_id).distinct().toList());
        }
        Map<String, Category_Master> mapCM = this.getCategoryMaster(products, category_ids);
        for (Products product : products) {
            Category_Master category_Master = mapCM.get(product.getCategory_id());
            ProductDetailsBean productDetailsBean = this.convertProductToBean(product, mapImg, category_Master);
            if (StringUtils.hasText(product.getVarient_mapping_id()) && !CollectionUtils.isEmpty(variantMap)
                    && variantMap.containsKey(product.getVarient_mapping_id())) {
                List<Products> variants = variantMap.get(product.getVarient_mapping_id());
                for (Products variant : variants) {
                    if (!variant.getId().equals(product.getId())) {
                        Category_Master cm = mapCM.get(product.getCategory_id());
                        ProductDetailsBean variantBean = this.convertProductToBean(variant, mapImg, cm);
                        if (CollectionUtils.isEmpty(productDetailsBean.getVarients())) {
                            productDetailsBean.setVarients(new ArrayList<>());
                        }
                        productDetailsBean.getVarients().add(variantBean);
                    }
                }
            }
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

    private ProductDetailsBean convertProductToBean(Products product, Map<String, String> mapImg,
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
        bean.setSecure(category_Master.isSecure_item());
        if (!CollectionUtils.isEmpty(product.getMedia())) {
            List<Media> listMedia = new ArrayList<>();
            List<Media> list = product.getMedia().stream()
                    .sorted(Comparator.comparing(Media::getOrder))
                    .toList();
            int order = 1;
            for (Media media : list) {
                if (!mapImg.containsKey(media.getDocument_id())) {
                    continue;
                }
                String key = mapImg.get(media.getDocument_id());
                media.setKey(key);
                media.setOrder(order);
                listMedia.add(media);
                order++;
            }
            bean.setMedia(listMedia);
        }
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

    private void validateRequest(ProductReqBean req, boolean isBulk) {
        if (!isBulk && !StringUtils.hasText(req.getCategory_id())) {
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_CATEGORY);
        }
        if (!StringUtils.hasText(req.getName())) {
            throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PRODUCT_NAME);
        }
//		if (!SERegExpUtils.standardTextValidation(req.getName())) {
//			throw new CustomIllegalArgumentsException(ResponseCode.INVALID_PRODUCT_NAME);
//		}
        if (CollectionUtils.isEmpty(req.getSub_categories())) {
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SUB_CATEGORY);
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
        if (StringUtils.hasText(req.getDescription()) && !SERegExpUtils.standardTextValidation(req.getDescription())) {
            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_PRODUCT_DESCRIPTION);
        }
    }

    private void validateRequestForBulk(List<ProductReqBean> list) {
        for (ProductReqBean req : list) {
            this.validateRequest(req, true);
        }
    }

    private Category_Master getCategoryMaster(String categoryId) {
        // Clear cache if expired
        if (System.currentTimeMillis() - lastCacheClearTime > CACHE_EXPIRY_TIME) {
            categoryMasterCache.clear();
            lastCacheClearTime = System.currentTimeMillis();
        }

        // Try to get from cache first
        Category_Master cached = categoryMasterCache.get(categoryId);
        if (cached != null) {
            return cached;
        }

        SEFilter filterC = new SEFilter(SEFilterType.AND);
        filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, categoryId));
        filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Category_Master categoryMaster = category_MasterService.repoFindOne(filterC);
        if (categoryMaster == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.CATEGORY_NOT_FOUND);
        }

        // Cache the result
        categoryMasterCache.put(categoryId, categoryMaster);
        return categoryMaster;
    }

    private Map<String, List<String>> getSubCategoriesMap(Category_Master categoryMaster) {
        if (CollectionUtils.isEmpty(categoryMaster.getGroups())) {
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
        List<SubCategory> sub_categories = categoryMaster.getSub_categories();
        if (CollectionUtils.isEmpty(sub_categories)) {
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }

        return sub_categories.stream().collect(Collectors.toMap(SubCategory::getName,
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

    private void validateMandatorySubCategories(Category_Master categoryMaster, List<SelectedSubCatagories> listSC) {
        Map<String, List<String>> mapSC = listSC.stream()
                .collect(Collectors.toMap(SelectedSubCatagories::getSub_category, SelectedSubCatagories::getSelected_attributes));
        List<SubCategory> sub_categories = categoryMaster.getSub_categories();
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
