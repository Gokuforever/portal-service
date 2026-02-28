package com.sorted.common.beans;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

import java.util.List;

@Data
@FieldNameConstants
public class SelectedSubCategories {
	private String sub_category;
	private List<String> selected_attributes;
}
