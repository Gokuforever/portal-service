package com.sorted.common.entity.mongo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants
@Document(collection = "ui_config")
public class Ui_Config extends BaseMongoEntity<String> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String page;
	private List<Section> sections;

	@Data
	public static class Section implements Serializable {
		private static final long serialVersionUID = 1L;
		private int order;
		private String component_type;
		private boolean clickable;
		private List<Content> content;
	}

	@Data
	public static class Content implements Serializable {
		private static final long serialVersionUID = 1L;
		private String label;
		private String key;
		private List<Attribute> attributes;
	}

	@Data
	public static class Attribute implements Serializable {
		private static final long serialVersionUID = 1L;
		private String label;
		private String key;
		private int order;
		private String image_url; // For image-related attributes
		private String alt_text; // For image-related attributes
		private String icon_url; // For icon-related attributes
	}

}
