package com.sorted.common.entity.mongo;

import com.sorted.common.enums.UserType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "file_upload_details")
public class File_Upload_Details extends BaseMongoEntity<String> {

	@Serial
	private static final long serialVersionUID = 1L;
	private String file_id;
	private UserType user_type;
	private String entity_id;
	private Integer document_type_id;
	private String file_url;
	private String file_extension;
	private String size;

}
