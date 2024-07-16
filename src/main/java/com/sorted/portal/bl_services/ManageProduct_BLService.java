package com.sorted.portal.bl_services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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

import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Products;
import com.sorted.commons.entity.mongo.Products.SelectedSubCatagories;
import com.sorted.commons.entity.mongo.Varient_Mapping;
import com.sorted.commons.entity.service.ProductService;
import com.sorted.commons.entity.service.Varient_Mapping_Service;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.portal.assisting.beans.ProductDetailsBean;
import com.sorted.portal.request.beans.FindProductBean;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/product")
public class ManageProduct_BLService {

	@Autowired
	private ProductService productService;

	@Autowired
	private Varient_Mapping_Service varient_Mapping_Service;

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

			Map<String, List<Products>> mapV = this.getVarients(product);
			ProductDetailsBean resBean = this.productToBean(mapV, product);
			return SEResponse.getBasicSuccessResponseObject(resBean, ResponseCode.SUCCESSFUL);
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("validateUserForLogin:: error occerred:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}

	private ProductDetailsBean productToBean(Map<String, List<Products>> mapV, Products... products) {
		List<Products> listP = Arrays.asList(products);
		return this.productToBean(mapV, listP).get(0);
	}

	private List<ProductDetailsBean> productToBean(Map<String, List<Products>> mapV, List<Products> listP) {
		List<ProductDetailsBean> resList = new ArrayList<>();
		for (Products product : listP) {
			ProductDetailsBean bean = new ProductDetailsBean();
			bean.setName(product.getName());
			bean.setId(product.getId());
			bean.setProduct_code(product.getProduct_code());
			bean.setSelling_price(product.getSelling_price());
			bean.setDescription(product.getDescription());
			if (!StringUtils.hasText(product.getVarient_mapping_id()) || CollectionUtils.isEmpty(mapV)
					|| !mapV.containsKey(product.getVarient_mapping_id())) {
				resList.add(bean);
				continue;
			}
			List<Products> list = mapV.get(product.getVarient_mapping_id());
			for (Products varient : list) {
				if (!varient.getId().equals(product.getId())) {
					ProductDetailsBean productToBean = this.productToBean(null, varient);
					bean.getVarients().add(productToBean);
				}
			}
			resList.add(bean);
		}
		return resList;
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
