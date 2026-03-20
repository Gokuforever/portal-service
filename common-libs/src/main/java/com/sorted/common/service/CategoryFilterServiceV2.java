package com.sorted.common.service;

import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.Category_Master;
import com.sorted.common.entity.mongo.Product_Master;
import com.sorted.common.entity.service.Category_MasterService;
import com.sorted.common.entity.service.Product_Master_Service;
import com.sorted.common.helper.AggregationFilter;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class CategoryFilterServiceV2 {

    private final Category_MasterService categoryMasterService;
    private final Product_Master_Service productMasterService;

    public List<Category_Master> getFilters() {
        // Step 1: Get all non-deleted product masters
        List<Product_Master> productMasters = getNonDeletedProductMasters();

        // Step 2: Extract subcategories and attributes from product masters
        FilterData filterData = extractFilterDataFromProductMasters(productMasters);

        // Step 3: Get all category masters
        List<Category_Master> allCategoryMasters = categoryMasterService.repoFindAll();

        // Step 4: Filter and build the result
        return buildFilteredCategoryMasters(allCategoryMasters, filterData);
    }

    /**
     * Retrieves all non-deleted product masters
     */
    private List<Product_Master> getNonDeletedProductMasters() {
        AggregationFilter.SEFilter filter = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filter.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        return productMasterService.repoFind(filter);
    }

    /**
     * Extracts subcategories and attributes from product masters, grouped by category
     */
    private FilterData extractFilterDataFromProductMasters(List<Product_Master> productMasters) {
        // Map: categoryId -> Set of subcategory names with products
        Map<String, Set<String>> subCategoriesByCategory = new HashMap<>();
        // Map: categoryId -> subcategoryName -> Set of attributes
        Map<String, Map<String, Set<String>>> attributesByCategoryAndSubCategory = new HashMap<>();

        for (Product_Master productMaster : productMasters) {
            String categoryId = productMaster.getCatagory_id();
            Map<String, List<String>> subCategories = productMaster.getSub_categories();

            if (categoryId == null || CollectionUtils.isEmpty(subCategories)) {
                continue;
            }

            subCategoriesByCategory.computeIfAbsent(categoryId, k -> new HashSet<>());
            attributesByCategoryAndSubCategory.computeIfAbsent(categoryId, k -> new HashMap<>());

            for (Map.Entry<String, List<String>> entry : subCategories.entrySet()) {
                String subCategoryName = entry.getKey();
                List<String> attributes = entry.getValue();

                if (subCategoryName == null) {
                    continue;
                }

                subCategoriesByCategory.get(categoryId).add(subCategoryName);

                if (!CollectionUtils.isEmpty(attributes)) {
                    attributesByCategoryAndSubCategory.get(categoryId)
                            .computeIfAbsent(subCategoryName, k -> new HashSet<>())
                            .addAll(attributes);
                }
            }
        }

        return new FilterData(subCategoriesByCategory, attributesByCategoryAndSubCategory);
    }

    /**
     * Builds filtered category masters containing only groups, subcategories, and attributes
     * that have associated products
     */
    private List<Category_Master> buildFilteredCategoryMasters(List<Category_Master> allCategoryMasters,
                                                               FilterData filterData) {
        List<Category_Master> filteredCategories = new ArrayList<>();

        for (Category_Master categoryMaster : allCategoryMasters) {
            Category_Master filteredCategory = buildFilteredCategory(categoryMaster, filterData);

            // Only add category if it has groups with valid subcategories
            if (filteredCategory != null && hasValidContent(filteredCategory)) {
                filteredCategories.add(filteredCategory);
            }
        }

        return filteredCategories;
    }

    /**
     * Builds a filtered category containing only groups and subcategories that have products
     */
    private Category_Master buildFilteredCategory(Category_Master originalCategory, FilterData filterData) {
        String categoryId = originalCategory.getId();

        // Skip if no products exist for this category
        if (!filterData.subCategoriesByCategory().containsKey(categoryId)) {
            return null;
        }

        Category_Master filteredCategory = new Category_Master();
        filteredCategory.setCategory_code(originalCategory.getCategory_code());
        filteredCategory.setName(originalCategory.getName());
        filteredCategory.setDeleted(false);
        filteredCategory.setId(originalCategory.getId());

        List<Category_Master.Groups> filteredGroups = new ArrayList<>();

        for (Category_Master.Groups group : originalCategory.getGroups()) {
            Category_Master.Groups filteredGroup = buildFilteredGroup(group, filterData, categoryId);

            // Only add group if it has subcategories with products
            if (!CollectionUtils.isEmpty(filteredGroup.getSub_categories())) {
                filteredGroups.add(filteredGroup);
            }
        }

        filteredCategory.setGroups(filteredGroups);
        return filteredCategory;
    }

    /**
     * Builds a filtered group containing only subcategories and attributes that have products
     */
    private Category_Master.Groups buildFilteredGroup(Category_Master.Groups originalGroup, FilterData filterData, String categoryId) {
        Category_Master.Groups filteredGroup = new Category_Master.Groups();
        filteredGroup.setGroup_id(originalGroup.getGroup_id());
        filteredGroup.setGroup_name(originalGroup.getGroup_name());
        filteredGroup.setGroup_order(originalGroup.getGroup_order());

        Set<String> subCategoriesForCategory = filterData.subCategoriesByCategory().get(categoryId);
        List<Category_Master.SubCategory> filteredSubCategories = new ArrayList<>();

        for (Category_Master.SubCategory subCategory : originalGroup.getSub_categories()) {
            // Only process subcategories that have associated products in THIS category
            if (subCategoriesForCategory != null && subCategoriesForCategory.contains(subCategory.getName())) {
                Category_Master.SubCategory filteredSubCategory = buildFilteredSubCategory(subCategory, filterData, categoryId);

                // Only add subcategory if it has attributes with products
                if (!CollectionUtils.isEmpty(filteredSubCategory.getAttributes())) {
                    filteredSubCategories.add(filteredSubCategory);
                }
            }
        }

        filteredGroup.setSub_categories(filteredSubCategories);
        return filteredGroup;
    }

    /**
     * Builds a filtered subcategory containing only attributes that have products in this category
     */
    private Category_Master.SubCategory buildFilteredSubCategory(Category_Master.SubCategory originalSubCategory,
                                                                 FilterData filterData, String categoryId) {
        Category_Master.SubCategory filteredSubCategory = new Category_Master.SubCategory();
        filteredSubCategory.setName(originalSubCategory.getName());
        filteredSubCategory.setInput_type(originalSubCategory.getInput_type());
        filteredSubCategory.setMandate(originalSubCategory.isMandate());
        filteredSubCategory.setOrder(originalSubCategory.getOrder());
        filteredSubCategory.setFilterable(originalSubCategory.isFilterable());
        filteredSubCategory.setRelated_filterable(originalSubCategory.isRelated_filterable());
        filteredSubCategory.setData_type(originalSubCategory.getData_type());

        // Get attributes for this specific category and subcategory
        Set<String> attributesForSubCategory = filterData.attributesByCategoryAndSubCategory()
                .getOrDefault(categoryId, Map.of())
                .getOrDefault(originalSubCategory.getName(), Set.of());

        // Filter attributes to only include those with products in THIS category
        List<String> filteredAttributes = originalSubCategory.getAttributes().stream()
                .filter(attributesForSubCategory::contains)
                .collect(Collectors.toList());

        filteredSubCategory.setAttributes(filteredAttributes);
        return filteredSubCategory;
    }

    /**
     * Checks if a category has valid content (non-empty groups with subcategories)
     */
    private boolean hasValidContent(Category_Master category) {
        return !CollectionUtils.isEmpty(category.getGroups()) &&
                category.getGroups().stream()
                        .anyMatch(group -> !CollectionUtils.isEmpty(group.getSub_categories()));
    }

    private record FilterData(
            Map<String, Set<String>> subCategoriesByCategory,
            Map<String, Map<String, Set<String>>> attributesByCategoryAndSubCategory) {
    }
}
