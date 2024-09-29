package com.sorted.portal.bl_services;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sorted.commons.beans.AddBulkProductReqBean;
import com.sorted.commons.beans.Media;
import com.sorted.commons.beans.ProductReqBean;
import com.sorted.commons.beans.SelectedSubCatagories;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Category_Master;
import com.sorted.commons.entity.mongo.Category_Master.SubCategory;
import com.sorted.commons.entity.mongo.File_Upload_Details;
import com.sorted.commons.entity.mongo.Products;
import com.sorted.commons.entity.mongo.Role;
import com.sorted.commons.entity.mongo.Seller;
import com.sorted.commons.entity.mongo.Varient_Mapping;
import com.sorted.commons.entity.service.Category_MasterService;
import com.sorted.commons.entity.service.File_Upload_Details_Service;
import com.sorted.commons.entity.service.ProductService;
import com.sorted.commons.entity.service.Seller_Service;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.entity.service.Varient_Mapping_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.All_Status.Seller_Status;
import com.sorted.commons.enums.DocumentType;
import com.sorted.commons.enums.Permission;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.enums.UserType;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterNode;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.GoogleDriveService;
import com.sorted.commons.utils.SERegExpUtils;
import com.sorted.portal.assisting.beans.ProductDetailsBean;
import com.sorted.portal.request.beans.FindProductBean;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/product")
public class ManageProduct_BLService {

	@Autowired
	private ProductService productService;

	@Autowired
	private Varient_Mapping_Service varient_Mapping_Service;

	@Autowired
	private Category_MasterService category_MasterService;

	@Autowired
	private Users_Service users_Service;

	@Autowired
	private Seller_Service seller_Service;

	@Autowired
	private File_Upload_Details_Service file_Upload_Details_Service;

	private final GoogleDriveService googleDriveService;

	public ManageProduct_BLService(GoogleDriveService googleDriveService) {
		this.googleDriveService = googleDriveService;
	}

	@Value("${se.default.page}")
	private int default_page;

	@Value("${se.default.size}")
	private int default_size;

	@PostMapping("/bulk/create")
	public SEResponse bulkCreate(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
		try {

			AddBulkProductReqBean req = request.getGenericRequestDataObject(AddBulkProductReqBean.class);
			CommonUtils.extractHeaders(httpServletRequest, req);
			UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Permission.EDIT,
					Activity.INVENTORY_MANAGEMENT);

			Role role = usersBean.getRole();
			UserType user_type = role.getUser_type();
			Seller seller = null;
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
					.collect(Collectors.toMap(e -> e.getDocument_id(), e -> e));
			if (!CollectionUtils.isEmpty(mapMedia)) {
				Set<String> document_ids = mapMedia.keySet();
				document_ids.remove(null);
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
				List<String> db_document_ids = repoFind.stream().map(e -> e.getId()).toList();
				if (!db_document_ids.containsAll(document_ids)) {
					throw new CustomIllegalArgumentsException(ResponseCode.IMAGES_NOT_FOUND);
				}
			}

			List<Products> listP = new ArrayList<>();

			for (ProductReqBean productReqBean : products) {
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
//				product.setVarient_mapping_id(varient_mapping_id);
				product.setDescription(
						StringUtils.hasText(productReqBean.getDescription()) ? productReqBean.getDescription() : null);

				if (!CollectionUtils.isEmpty(productReqBean.getMedia())) {
					List<Media> listMedia = productReqBean.getMedia().stream()
							.filter(e -> StringUtils.hasText(e.getDocument_id())).toList();
					product.setMedia(listMedia);
				}

				listP.add(product);
			}

			productService.bulkCreate(listP, usersBean.getId());

			return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("validateUserForLogin:: error occerred:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}

	@PostMapping("/create")
	public SEResponse create(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
		try {
			ProductReqBean req = request.getGenericRequestDataObject(ProductReqBean.class);
			CommonUtils.extractHeaders(httpServletRequest, req);
			UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Permission.EDIT,
					Activity.INVENTORY_MANAGEMENT);

			Role role = usersBean.getRole();
			UserType user_type = role.getUser_type();
			Seller seller = null;
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

			String varient_mapping_id = this.upsertAndGetVarientMappingId(req, usersBean, role);

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
				Set<String> document_ids = req.getMedia().stream().filter(e -> StringUtils.hasText(e.getDocument_id()))
						.map(Media::getDocument_id).collect(Collectors.toSet());
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
				List<String> db_document_ids = repoFind.stream().map(e -> e.getId()).toList();
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
			log.error("validateUserForLogin:: error occerred:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}

	@PostMapping("/edit")
	public SEResponse edit(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
		try {
			ProductReqBean req = request.getGenericRequestDataObject(ProductReqBean.class);
			CommonUtils.extractHeaders(httpServletRequest, req);
			UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Permission.EDIT,
					Activity.INVENTORY_MANAGEMENT);

			Role role = usersBean.getRole();
			UserType user_type = role.getUser_type();
			Seller seller = null;
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

			String varient_mapping_id = this.upsertAndGetVarientMappingId(req, usersBean, role);

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
			if (!CollectionUtils.isEmpty(req.getMedia())) {
				Set<String> document_ids = req.getMedia().stream().filter(e -> StringUtils.hasText(e.getDocument_id()))
						.map(Media::getDocument_id).collect(Collectors.toSet());
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
				List<String> db_document_ids = repoFind.stream().map(e -> e.getId()).toList();
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
			log.error("validateUserForLogin:: error occerred:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}

	@PostMapping("/find")
	public SEResponse find(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
		try {
			FindProductBean req = request.getGenericRequestDataObject(FindProductBean.class);
			CommonUtils.extractHeaders(httpServletRequest, req);
			UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.PRODUCTS,
					Activity.INVENTORY_MANAGEMENT);
			SEFilter filterSE = this.createFilterForProductList(req, usersBean);

//			FindResBean bean = new FindResBean();
//			int page = req.getPage();
//			int size = req.getSize();
//			if (size < 1) {
//				page = default_page;
//				size = default_size;
//			}
//			if (page == 0) {
//				long total_count = productService.countByFilter(filterSE);
//				if (total_count <= 0) {
//					return SEResponse.getBasicSuccessResponseObject(bean, ResponseCode.NO_RECORD);
//				}
//				bean.setTotal_count(total_count);
//			}
//			
//			Pagination pagination = new Pagination(page, size);
//			filterSE.setPagination(pagination);

			List<Products> listP = productService.repoFind(filterSE);
			if (CollectionUtils.isEmpty(listP)) {
				return SEResponse.getEmptySuccessResponse(ResponseCode.NO_RECORD);
			}

			Map<String, String> mapImg = this.filterAndFetchImgMap(listP);

			Set<String> seller_ids = this.getSellerByPincode(req.getPincode());

			List<ProductDetailsBean> resList = this.convertToBean(null, listP, seller_ids, mapImg);

//			bean.setList(resList);

			return SEResponse.getBasicSuccessResponseList(resList, ResponseCode.SUCCESSFUL);
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("validateUserForLogin:: error occerred:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}

	@PostMapping("/delete")
	public SEResponse delete(@RequestBody SERequest request) {
		try {

			FindProductBean req = request.getGenericRequestDataObject(FindProductBean.class);
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
			log.error("validateUserForLogin:: error occerred:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}

	@PostMapping("/findOne")
	public SEResponse findOne(@RequestBody SERequest request) {
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

			Map<String, List<String>> relatedFilters = new HashMap<>();

			product.getSelected_sub_catagories().stream().forEach(s -> {
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

			Map<String, List<Products>> mapV = this.getVarients(product);
			Set<String> seller_ids = this.getSellerByPincode(req.getPincode());
			Map<String, String> mapImg = this.filterAndFetchImgMap(Arrays.asList(product));

			ProductDetailsBean resBean = this.productToBean(listRI, mapV, product, seller_ids, mapImg);

			return SEResponse.getBasicSuccessResponseObject(resBean, ResponseCode.SUCCESSFUL);
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("validateUserForLogin:: error occerred:: {}", e.getMessage());
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
			String file_id = googleDriveService.uploadPhoto(file, usersBean, DocumentType.PRODUCT_IMAGE);
			return SEResponse.getBasicSuccessResponseObject(file_id, ResponseCode.SUCCESSFUL);
		} catch (IOException | GeneralSecurityException e) {
			e.printStackTrace();
			return SEResponse.getBadRequestFailureResponse(ResponseCode.ERR_0001);
		}
	}

	private Map<String, String> filterAndFetchImgMap(List<Products> listP) {
		// Extract image document IDs from products
		Set<String> imageIds = listP.stream()
				.filter(products -> products.getMedia() != null && !products.getMedia().isEmpty())
				.flatMap(products -> products.getMedia().stream().map(Media::getDocument_id))
				.collect(Collectors.toSet());

		// Initialize maps for file upload details and images
		Map<String, String> mapFUD = new HashMap<>();
		Map<String, String> mapImg = new HashMap<>();

		// Process only if imageIds list is not empty
		if (!CollectionUtils.isEmpty(imageIds)) {
			// Create a filter to retrieve file upload details based on image IDs
			SEFilter filterFUD = new SEFilter(SEFilterType.AND);
			filterFUD.addClause(WhereClause.in(BaseMongoEntity.Fields.id, CommonUtils.convertS2L(imageIds)));
			filterFUD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			// Retrieve file upload details
			List<File_Upload_Details> listFUD = file_Upload_Details_Service.repoFind(filterFUD);

			// Populate mapFUD if file upload details list is not empty
			if (!CollectionUtils.isEmpty(listFUD)) {
				mapFUD = listFUD.stream()
						.collect(Collectors.toMap(File_Upload_Details::getId, File_Upload_Details::getDocument_id));
			}

			// Map the image IDs that have corresponding entries in mapFUD
			mapImg.putAll(imageIds.stream().filter(mapFUD::containsKey) // Only consider IDs present in mapFUD
					.collect(Collectors.toMap(id -> id, mapFUD::get))); // Map image ID to corresponding document ID
		}
		return mapImg;
	}

	private SEFilter createFilterForProductList(FindProductBean req, UsersBean usersBean) {
		SEFilter filterSE = new SEFilter(SEFilterType.AND);
		switch (usersBean.getRole().getUser_type()) {
		case SELLER:
			filterSE.addClause(WhereClause.eq(Products.Fields.seller_id, usersBean.getRole().getSeller_id()));
			filterSE.addClause(WhereClause.eq(Products.Fields.seller_code, usersBean.getRole().getSeller_code()));
			break;
		default:
			SEFilter filterS = new SEFilter(SEFilterType.AND);
			filterS.addClause(WhereClause.notEq(Seller.Fields.status, Seller_Status.ACTIVE.name()));
			filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
			List<Seller> sellers = seller_Service.repoFind(filterS);
			if (!CollectionUtils.isEmpty(sellers)) {
				List<String> ids = sellers.parallelStream().map(Seller::getId).toList();
				filterSE.addClause(WhereClause.nin(Products.Fields.seller_id, ids));
			}
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
		return filterSE;
	}

	private Set<String> getSellerByPincode(String pincode) {
		Set<String> seller_ids = new HashSet<>();
		if (StringUtils.hasText(pincode)) {
			if (!SERegExpUtils.isPincode(pincode)) {
				throw new CustomIllegalArgumentsException(ResponseCode.INVALID_PINCODE);
			}
			SEFilter filterS = new SEFilter(SEFilterType.AND);
			filterS.addClause(WhereClause.eq(Seller.Fields.serviceable_pincodes, pincode));
			filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			List<Seller> sellers = seller_Service.repoFind(filterS);
			if (!CollectionUtils.isEmpty(sellers)) {
				seller_ids = sellers.stream().map(e -> e.getId()).collect(Collectors.toSet());
			}
		}
		return seller_ids;
	}

	private void makeValuesUnique(Map<String, List<String>> map) {
		for (Map.Entry<String, List<String>> entry : map.entrySet()) {
			List<String> uniqueList = new ArrayList<>(new HashSet<>(entry.getValue()));
			entry.setValue(uniqueList);
		}
	}

	private ProductDetailsBean productToBean(List<Products> relatedProducts, Map<String, List<Products>> variantMap,
			Products product, Set<String> seller_ids, Map<String, String> mapImg) {
		ProductDetailsBean mainProductDetails = this.convertToBean(variantMap, Arrays.asList(product), null, mapImg)
				.get(0);

		if (!CollectionUtils.isEmpty(relatedProducts)) {
			List<ProductDetailsBean> relatedProductDetailsList = new ArrayList<>();
			for (Products temp : relatedProducts) {
				relatedProductDetailsList.add(this.convertProductToBean(temp, seller_ids, mapImg));
			}
			mainProductDetails.setRelated_products(relatedProductDetailsList);
		}

		return mainProductDetails;
	}

	private List<ProductDetailsBean> convertToBean(Map<String, List<Products>> variantMap, List<Products> products,
			Set<String> seller_ids, Map<String, String> mapImg) {
		List<ProductDetailsBean> productDetailsList = new ArrayList<>();
		for (Products product : products) {
			ProductDetailsBean productDetailsBean = this.convertProductToBean(product, seller_ids, mapImg);
			if (StringUtils.hasText(product.getVarient_mapping_id()) && !CollectionUtils.isEmpty(variantMap)
					&& variantMap.containsKey(product.getVarient_mapping_id())) {
				List<Products> variants = variantMap.get(product.getVarient_mapping_id());
				for (Products variant : variants) {
					if (!variant.getId().equals(product.getId())) {
						ProductDetailsBean variantBean = this.convertProductToBean(variant, seller_ids, mapImg);
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

	private ProductDetailsBean convertProductToBean(Products product, Set<String> seller_ids,
			Map<String, String> mapImg) {
		ProductDetailsBean bean = new ProductDetailsBean();
		bean.setName(product.getName());
		bean.setId(product.getId());
		bean.setProduct_code(product.getProduct_code());
		bean.setSelling_price(CommonUtils.paiseToRupee(product.getSelling_price()));
		bean.setMrp(CommonUtils.paiseToRupee(product.getMrp()));
		bean.setQuantity(product.getQuantity().intValue());
		bean.setDescription(product.getDescription());
		if (!CollectionUtils.isEmpty(product.getMedia())) {
			List<Media> listMedia = new ArrayList<>();
			List<Media> list = product.getMedia().stream().sorted((o1, o2) -> o1.getOrder().compareTo(o2.getOrder()))
					.toList();
			int order = 1;
			for (Media media : list) {
				if (!mapImg.containsKey(media.getDocument_id())) {
					continue;
				}
				String document_id = mapImg.get(media.getDocument_id());
				media.setDocument_id(document_id);
				media.setOrder(order);
				listMedia.add(media);
				order++;
			}
			bean.setMedia(listMedia);
		}
		if (!CollectionUtils.isEmpty(seller_ids)) {
			bean.set_deliverable(seller_ids.contains(product.getSeller_id()));
		}
		return bean;
	}

	private Map<String, List<Products>> getVarients(Products... products) {
		List<Products> listP = Arrays.asList(products);
		return this.getVarients(listP);
	}

	private Map<String, List<Products>> getVarients(List<Products> listP) {
		Set<String> varientMappingIds = listP.stream().map(e -> e.getVarient_mapping_id()).collect(Collectors.toSet());
		varientMappingIds.remove(null);
		if (CollectionUtils.isEmpty(varientMappingIds)) {
			return new HashMap<>();
		}
		List<String> product_ids = listP.stream().map(Products::getId).toList();
		SEFilter filterVM = new SEFilter(SEFilterType.AND);
		filterVM.addClause(WhereClause.in(BaseMongoEntity.Fields.id, CommonUtils.convertS2L(varientMappingIds)));
		filterVM.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

		List<Varient_Mapping> listVM = varient_Mapping_Service.repoFind(filterVM);
		if (CollectionUtils.isEmpty(listVM)) {
			return new HashMap<>();
		}
		List<String> varient_ids = listVM.stream().map(Varient_Mapping::getId).toList();
		SEFilter filterVarients = new SEFilter(SEFilterType.AND);
		filterVarients.addClause(WhereClause.in(Products.Fields.varient_mapping_id, varient_ids));
		filterVarients.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
		filterVarients.addClause(WhereClause.nin(BaseMongoEntity.Fields.id, product_ids));

		List<Products> varients = productService.repoFind(filterVarients);
		if (CollectionUtils.isEmpty(varients)) {
			return new HashMap<>();
		}
		Map<String, List<Products>> mapV = new HashMap<>();
		varients.stream().forEach(e -> {
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
		SEFilter filterC = new SEFilter(SEFilterType.AND);
		filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, categoryId));
		filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

		Category_Master categoryMaster = category_MasterService.repoFindOne(filterC);
		if (categoryMaster == null) {
			throw new CustomIllegalArgumentsException(ResponseCode.CATEGORY_NOT_FOUND);
		}
		return categoryMaster;
	}

	private Map<String, List<String>> getSubCategoriesMap(Category_Master categoryMaster) {
		if (CollectionUtils.isEmpty(categoryMaster.getGroups())) {
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
		List<SubCategory> sub_catagories = categoryMaster.getSub_categories();
		if (CollectionUtils.isEmpty(sub_catagories)) {
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}

		return sub_catagories.stream().collect(Collectors.toMap(e -> e.getName(),
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
			if (!CollectionUtils.isEmpty(attributes) && !attributes.containsAll(val)) {
				throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SUB_CATEGORY);
			}

			SelectedSubCatagories subCatagories = new SelectedSubCatagories();
			subCatagories.setSub_category(key);
			subCatagories.setSelected_attributes(val);
			listSC.add(subCatagories);
		});
		return listSC;
	}

	private void validateMandatorySubCategories(Category_Master categoryMaster, List<SelectedSubCatagories> listSC) {
		Map<String, List<String>> mapSC = listSC.stream()
				.collect(Collectors.toMap(e -> e.getSub_category(), e -> e.getSelected_attributes()));
		List<SubCategory> sub_categories = categoryMaster.getSub_categories();
		List<SubCategory> sorted = sub_categories.stream().sorted((o1, o2) -> o1.getOrder()).toList();
		sorted.forEach(e -> {
			if (e.isMandate() && (!mapSC.containsKey(e.getName()) || CollectionUtils.isEmpty(mapSC.get(e.getName())))) {
				throw new CustomIllegalArgumentsException(e.getName() + " is mandatory.");
			}
		});
	}

	private String upsertAndGetVarientMappingId(ProductReqBean req, UsersBean usersBean, Role role) {
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

			String varient_name = CommonUtils.toTitleCase(req.getVarient_name());

			SEFilter filterV = new SEFilter(SEFilterType.AND);
			filterV.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
			filterV.addClause(WhereClause.eq(Varient_Mapping.Fields.name, varient_name));
			if (role.getUser_type() == UserType.SELLER) {
				filterV.addClause(WhereClause.eq(Varient_Mapping.Fields.seller_id, usersBean.getRole().getSeller_id()));
			}
			long count = varient_Mapping_Service.countByFilter(filterV);
			if (count > 0) {
				throw new CustomIllegalArgumentsException(ResponseCode.DUPLICATE_VARIENT);
			}

			Varient_Mapping varient = new Varient_Mapping();
			varient.setName(varient_name);
			if (role.getUser_type() == UserType.SELLER) {
				varient.setSeller_id(role.getSeller_id());
				varient.setSeller_code(role.getSeller_code());
			}

			varient = varient_Mapping_Service.create(varient, usersBean.getId());
			return varient.getId();
		}
		return null;
	}

}
