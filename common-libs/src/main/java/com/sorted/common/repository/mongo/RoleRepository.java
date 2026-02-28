package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Role;
import com.sorted.common.helper.BaseMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends BaseMongoRepository<String, Role>{

	@Override
	default Class<Role> getEntityType() {
		return Role.class;
	}
}
