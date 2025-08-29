package com.sorted.portal.service;

import com.sorted.commons.beans.SelectedSubCategories;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Category_Master;
import com.sorted.commons.entity.mongo.Products;
import com.sorted.commons.entity.service.Category_MasterService;
import com.sorted.commons.entity.service.ProductService;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class CategoryFilterService {

    private final Category_MasterService categoryMasterService;
    private final ProductService productService;

    public List<Category_Master> getFilters() {
        // Step 1: Get all non-deleted products
        List<Products> products = getNonDeletedProducts();

        // Step 2: Extract subcategories and attributes from products
        FilterData filterData = extractFilterDataFromProducts(products);

        // Step 3: Get all category masters
        List<Category_Master> allCategoryMasters = categoryMasterService.repoFindAll();

        // Step 4: Filter and build the result
        return buildFilteredCategoryMasters(allCategoryMasters, filterData);
    }

    /**
     * Retrieves all non-deleted products
     */
    private List<Products> getNonDeletedProducts() {
        SEFilter filterP = new SEFilter(SEFilterType.AND);
        filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        return productService.repoFind(filterP);
    }

    /**
     * Extracts subcategories and attributes from products that have associated products
     */
    private FilterData extractFilterDataFromProducts(List<Products> products) {
        List<SelectedSubCategories> selectedSubCategories = products.stream()
                .flatMap(product -> product.getSelected_sub_catagories().stream())
                .toList();

        Set<String> subCategoriesWithProducts = selectedSubCategories.stream()
                .map(SelectedSubCategories::getSub_category)
                .collect(Collectors.toSet());

        Set<String> attributesWithProducts = selectedSubCategories.stream()
                .flatMap(subCat -> subCat.getSelected_attributes().stream())
                .collect(Collectors.toSet());

        return new FilterData(subCategoriesWithProducts, attributesWithProducts);
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
            if (hasValidContent(filteredCategory)) {
                filteredCategories.add(filteredCategory);
            }
        }

        return filteredCategories;
    }

    /**
     * Builds a filtered category containing only groups and subcategories that have products
     */
    private Category_Master buildFilteredCategory(Category_Master originalCategory, FilterData filterData) {
        Category_Master filteredCategory = new Category_Master();
        filteredCategory.setCategory_code(originalCategory.getCategory_code());
        filteredCategory.setName(originalCategory.getName());
        filteredCategory.setDeleted(false);
        filteredCategory.setId(originalCategory.getId());

        List<Category_Master.Groups> filteredGroups = new ArrayList<>();

        for (Category_Master.Groups group : originalCategory.getGroups()) {
            Category_Master.Groups filteredGroup = buildFilteredGroup(group, filterData);

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
    private Category_Master.Groups buildFilteredGroup(Category_Master.Groups originalGroup, FilterData filterData) {
        Category_Master.Groups filteredGroup = new Category_Master.Groups();
        filteredGroup.setGroup_id(originalGroup.getGroup_id());
        filteredGroup.setGroup_name(originalGroup.getGroup_name());
        filteredGroup.setGroup_order(originalGroup.getGroup_order());

        List<Category_Master.SubCategory> filteredSubCategories = new ArrayList<>();

        for (Category_Master.SubCategory subCategory : originalGroup.getSub_categories()) {
            // Only process subcategories that have associated products
            if (filterData.getSubCategoriesWithProducts().contains(subCategory.getName())) {
                Category_Master.SubCategory filteredSubCategory = buildFilteredSubCategory(subCategory, filterData);

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
     * Builds a filtered subcategory containing only attributes that have products
     */
    private Category_Master.SubCategory buildFilteredSubCategory(Category_Master.SubCategory originalSubCategory,
                                                                 FilterData filterData) {
        Category_Master.SubCategory filteredSubCategory = new Category_Master.SubCategory();
        filteredSubCategory.setName(originalSubCategory.getName());
        filteredSubCategory.setInput_type(originalSubCategory.getInput_type());
        filteredSubCategory.setMandate(originalSubCategory.isMandate());
        filteredSubCategory.setOrder(originalSubCategory.getOrder());
        filteredSubCategory.setFilterable(originalSubCategory.isFilterable());
        filteredSubCategory.setRelated_filterable(originalSubCategory.isRelated_filterable());
        filteredSubCategory.setData_type(originalSubCategory.getData_type());

        // Filter attributes to only include those with products
        List<String> filteredAttributes = originalSubCategory.getAttributes().stream()
                .filter(filterData.getAttributesWithProducts()::contains)
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

    /**
     * Helper class to hold filter data extracted from products
     */
    @Getter
    private static class FilterData {
        private final Set<String> subCategoriesWithProducts;
        private final Set<String> attributesWithProducts;

        public FilterData(Set<String> subCategoriesWithProducts, Set<String> attributesWithProducts) {
            this.subCategoriesWithProducts = subCategoriesWithProducts;
            this.attributesWithProducts = attributesWithProducts;
        }

    }
}
