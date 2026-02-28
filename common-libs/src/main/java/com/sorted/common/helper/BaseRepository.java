package com.sorted.common.helper;

import com.sorted.common.helper.AggregationFilter.SEFilter;

import java.util.List;
import java.util.Optional;

public interface BaseRepository<T, K> {

	List<T> repoFindAll();

	T repoFindOne(SEFilter f);

	List<T> repoFind(SEFilter f);

	T create(T obj, String cudby);

	List<T> bulkCreate(List<T> list, String cudby);

	long countByFilter(SEFilter f);

	long totalCount();

	T update(K id, T obj, String cud_by);

	T upsert(K id, T obj, String cud_by);

	void deleteOne(K id, String cud_by);

	Optional<T> findById(K id);

}
