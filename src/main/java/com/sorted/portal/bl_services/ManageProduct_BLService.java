package com.sorted.portal.bl_services;

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
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sorted.commons.beans.ProductReqBean;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Category_Master;
import com.sorted.commons.entity.mongo.Products;
import com.sorted.commons.entity.mongo.Products.SelectedSubCatagories;
import com.sorted.commons.entity.mongo.Varient_Mapping;
import com.sorted.commons.entity.service.Category_MasterService;
import com.sorted.commons.entity.service.ProductService;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.entity.service.Varient_Mapping_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.Permission;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.ReqBaseBean;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.CommonUtils;
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

	@PostMapping("/create")
	public SEResponse create(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
		try {
			ProductReqBean req = request.getGenericRequestDataObject(ProductReqBean.class);
			this.extractHeaders(httpServletRequest, req);
			UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), req.getReq_role_id(),
					Activity.PRODUCTS, Permission.EDIT);
			switch (usersBean.getRole().getUser_type()) {
			case SELLER:
			case SUPER_ADMIN:
				break;
			default:
				throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
			}
			if (!StringUtils.hasText(req.getCatagory_id())) {
				throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_CATEGORY);
			}
			if (!StringUtils.hasText(req.getName())) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PRODUCT_NAME);
			}
			if (!SERegExpUtils.standardTextValidation(req.getName())) {
				throw new CustomIllegalArgumentsException(ResponseCode.INVALID_PRODUCT_NAME);
			}
			if (CollectionUtils.isEmpty(req.getSub_catagories())) {
				throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SUB_CATEGORY);
			}
			SEFilter filterC = new SEFilter(SEFilterType.AND);
			filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getCatagory_id()));
			filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			Category_Master category_Master = category_MasterService.repoFindOne(filterC);
			if (category_Master == null) {
				throw new CustomIllegalArgumentsException(ResponseCode.CATEGORY_NOT_FOUND);
			}
			if (CollectionUtils.isEmpty(category_Master.getSub_categories())) {
				throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
			}
			Map<String, List<String>> mapSC = category_Master.getSub_categories().stream()
					.collect(Collectors.toMap(e -> e.getName(),
							e -> CollectionUtils.isEmpty(e.getAttributes()) ? new ArrayList<>() : e.getAttributes()));
			req.getSub_catagories().forEach((key, val) -> {
				if (!StringUtils.hasText(key)) {
					throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SUB_CATEGORY);
				}
				if (!CollectionUtils.isEmpty(val)) {
					throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SUB_CATEGORY_VAL);
				}
				if (!mapSC.containsKey(key)) {
					throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SUB_CATEGORY);
				}
				List<String> attributes = mapSC.get(key);
				if (!CollectionUtils.isEmpty(attributes)) {
					if (!attributes.containsAll(val)) {
						throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SUB_CATEGORY);
					}
				}
			});

			category_Master.getSub_categories().stream().forEach(e -> {
				if (e.isMandate() && !req.getSub_catagories().containsKey(e.getName())) {
					throw new CustomIllegalArgumentsException(e.getName() + " is mandatory.");
				}
			});
			if (!StringUtils.hasText(req.getMrp())) {
				throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_MRP);
			}
			if (!StringUtils.hasText(req.getSelling_price())) {
				throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_SP);
			}
			Products product = new Products();

			// TODO:: images url pending
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("validateUserForLogin:: error occerred:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}

		return null;

	}

	private <T extends ReqBaseBean> void extractHeaders(HttpServletRequest httpServletRequest, T bean) {
		try {
			String req_user_id = httpServletRequest.getAttribute("req_user_id").toString();
			String req_role_id = httpServletRequest.getAttribute("req_role_id").toString();
			bean.setReq_user_id(req_user_id);
			bean.setReq_role_id(req_role_id);
		} catch (Exception e2) {
			throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
		}
	}

	@PostMapping("/find")
	public SEResponse find(@RequestBody SERequest request) {
		try {
			FindProductBean req = request.getGenericRequestDataObject(FindProductBean.class);
			SEFilter filterSE = new SEFilter(SEFilterType.AND);
			if (!CollectionUtils.isEmpty(req.getFilters())) {
				for (Entry<String, List<String>> entry : req.getFilters().entrySet()) {
					if (StringUtils.hasText(entry.getKey()) && !CollectionUtils.isEmpty(entry.getValue())) {
						Map<String, String> keymap = new HashMap<>();
						keymap.put(SelectedSubCatagories.Fields.sub_category, entry.getKey());
						Map<String, List<?>> valmap = new HashMap<>();
						valmap.put(SelectedSubCatagories.Fields.selected_attributes, entry.getValue());
						filterSE.addClause(
								WhereClause.elem_match(Products.Fields.selected_sub_catagories, keymap, valmap));
					}
				}
			}
			filterSE.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			List<Products> listP = productService.repoFind(filterSE);
			if (CollectionUtils.isEmpty(listP)) {
				return SEResponse.getEmptySuccessResponse(ResponseCode.NO_RECORD);
			}

			Map<String, List<Products>> mapV = this.getVarients(listP);

			List<ProductDetailsBean> resList = this.productToBean(mapV, listP);

			return SEResponse.getBasicSuccessResponseList(resList, ResponseCode.SUCCESSFUL);
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
			for (Entry<String, List<String>> entry : relatedFilters.entrySet()) {
				if (StringUtils.hasText(entry.getKey()) && !CollectionUtils.isEmpty(entry.getValue())) {
					Map<String, String> keymap = new HashMap<>();
					keymap.put(SelectedSubCatagories.Fields.sub_category, entry.getKey());
					Map<String, List<?>> valmap = new HashMap<>();
					valmap.put(SelectedSubCatagories.Fields.selected_attributes, entry.getValue());
					filterRI.addClause(WhereClause.elem_match(Products.Fields.selected_sub_catagories, keymap, valmap));
				}
			}
			filterRI.addClause(WhereClause.notEq(BaseMongoEntity.Fields.id, product.getId()));
			filterRI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			List<Products> listRI = productService.repoFind(filterRI);

			Map<String, List<Products>> mapV = this.getVarients(product);
			ProductDetailsBean resBean = this.productToBean(listRI, mapV, product);

			return SEResponse.getBasicSuccessResponseObject(resBean, ResponseCode.SUCCESSFUL);
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("validateUserForLogin:: error occerred:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}

	private void makeValuesUnique(Map<String, List<String>> map) {
		for (Map.Entry<String, List<String>> entry : map.entrySet()) {
			List<String> uniqueList = new ArrayList<>(new HashSet<>(entry.getValue()));
			entry.setValue(uniqueList);
		}
	}

	private List<ProductDetailsBean> productToBean(Map<String, List<Products>> variantMap, List<Products> listP) {
		return this.convertToBean(variantMap, listP);
	}

	private ProductDetailsBean productToBean(List<Products> relatedProducts, Map<String, List<Products>> variantMap,
			Products product) {
		ProductDetailsBean mainProductDetails = this.convertToBean(variantMap, Arrays.asList(product)).get(0);

		if (!CollectionUtils.isEmpty(relatedProducts)) {
			List<ProductDetailsBean> relatedProductDetailsList = new ArrayList<>();
			for (Products temp : relatedProducts) {
				relatedProductDetailsList.add(this.convertProductToBean(temp));
			}
			mainProductDetails.setRelated_products(relatedProductDetailsList);
		}

		return mainProductDetails;
	}

	private List<ProductDetailsBean> convertToBean(Map<String, List<Products>> variantMap, List<Products> products) {
		List<ProductDetailsBean> productDetailsList = new ArrayList<>();
		for (Products product : products) {
			ProductDetailsBean productDetailsBean = this.convertProductToBean(product);
			if (StringUtils.hasText(product.getVarient_mapping_id()) && !CollectionUtils.isEmpty(variantMap)
					&& variantMap.containsKey(product.getVarient_mapping_id())) {
				List<Products> variants = variantMap.get(product.getVarient_mapping_id());
				for (Products variant : variants) {
					if (!variant.getId().equals(product.getId())) {
						ProductDetailsBean variantBean = this.convertProductToBean(variant);
						productDetailsBean.getVarients().add(variantBean);
					}
				}
			}
			productDetailsList.add(productDetailsBean);
		}
		return productDetailsList;
	}

	private ProductDetailsBean convertProductToBean(Products product) {
		ProductDetailsBean bean = new ProductDetailsBean();
		bean.setName(product.getName());
		bean.setId(product.getId());
		bean.setProduct_code(product.getProduct_code());
		bean.setSelling_price(product.getSelling_price());
		bean.setDescription(product.getDescription());
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
		SEFilter filterVM = new SEFilter(SEFilterType.AND);
		filterVM.addClause(WhereClause.in(BaseMongoEntity.Fields.id, CommonUtils.convertS2L(varientMappingIds)));
		filterVM.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

		List<Varient_Mapping> listVM = varient_Mapping_Service.repoFind(filterVM);
		if (CollectionUtils.isEmpty(listVM)) {
			return new HashMap<>();
		}
		List<String> varient_ids = listVM.stream().map(Varient_Mapping::getId).collect(Collectors.toList());
		SEFilter filterVarients = new SEFilter(SEFilterType.AND);
		filterVarients.addClause(WhereClause.in(Products.Fields.varient_mapping_id, varient_ids));
		filterVarients.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

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

}
