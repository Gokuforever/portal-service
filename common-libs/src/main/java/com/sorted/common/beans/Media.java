package com.sorted.common.beans;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Media {
	private Integer order; // Order in which the media should be displayed
	private String cdn_url;

	public enum MediaType {
		IMAGE;
	}
}
