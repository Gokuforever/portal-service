package com.sorted.common.beans;

import com.sorted.common.enums.Permission;
import lombok.Data;

@Data
public class ActivityPermissions {

	private Permission permission;
	private Integer permissions_id;

	public void setPermission(Permission permission) {
		this.permission = permission;
		this.permissions_id = permission.getId();
	}

}
