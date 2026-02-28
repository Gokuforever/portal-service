package com.sorted.common.entity.mongo;

import com.sorted.common.beans.Role_Activity_Permissions;
import com.sorted.common.enums.All_Status.Role_Status;
import com.sorted.common.enums.UserType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@FieldNameConstants
@EqualsAndHashCode(callSuper = false)
@Document(collection = "role")
public class Role extends BaseMongoEntity<String> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String code;
	private String name;
	private String seller_id;
	private String seller_code;
	private Integer status;
	private Integer user_type_id;
	private UserType user_type;
	private List<Role_Activity_Permissions> role_permissions;

	public void setStatus(Role_Status status) {
		this.status = status.getId();
	}

}
