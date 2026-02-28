package com.sorted.common.entity.service;

import com.sorted.common.enums.ResponseCode;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.BaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Optional;

public abstract class GenericEntityServiceImpl<K, T, R extends BaseRepository<T, K>> implements BaseRepository<T, K> {

	protected abstract Class<R> getRepoClass();

	protected abstract void validateBeforeCreate(T inE) throws RuntimeException;

	protected abstract void validateBeforeUpdate(K id, T inE) throws RuntimeException;

	protected abstract void validateBeforeDelete(K id) throws RuntimeException;

	public void validateCreate(T inE) {
		this.validateBeforeCreate(inE);
	}

	public void validateUpdate(K id, T inE) {
		this.validateBeforeUpdate(id, inE);
	}

	public void validateUpsert(T inE) {
		this.validateBeforeCreate(inE);
	}

	public void validateDelete(K id) {
		this.validateBeforeDelete(id);
	}

	private R repository;

	@Autowired
	private void setApplicationContext(ApplicationContext applicationContext) {
		repository = applicationContext.getBean(getRepoClass());
	}

	@Override
	public List<T> repoFindAll() {
		return repository.repoFindAll();
	}

	@Override
	public List<T> repoFind(SEFilter filter) {
		return repository.repoFind(filter);
	}

	@Override
	public T repoFindOne(SEFilter filter) {
		return repository.repoFindOne(filter);
	}

	@Override
	public long totalCount() {
		return repository.totalCount();
	}

	@Override
	public T create(T entity, String cudby) {
		this.validateBeforeCreate(entity);
		return repository.create(entity, cudby);
	}

	@Override
	public List<T> bulkCreate(List<T> entity, String cudby) {
		for (T t : entity) {
			this.validateBeforeCreate(t);
		}
		return repository.bulkCreate(entity, cudby);
	}

	@Override
	public long countByFilter(SEFilter filter) {
		return repository.countByFilter(filter);
	}

	@Override
	public T update(K id, T entity, String cudby) {
		if (id == null) {
			throw new CustomIllegalArgumentsException(ResponseCode.MISSING_ENTITY);
		}
		this.validateBeforeUpdate(id, entity);
		return repository.update(id, entity, cudby);
	}

	@Override
	public T upsert(K id, T obj, String cud_by) {
		if (id == null) {
			return this.create(obj, cud_by);
		}
		return this.update(id, obj, cud_by);
	}

	@Override
	public void deleteOne(K id, String cud_by) {
		this.validateBeforeDelete(id);
		repository.deleteOne(id, cud_by);
	}

	@Override
	public Optional<T> findById(K id) {
		return repository.findById(id);
	}
}
