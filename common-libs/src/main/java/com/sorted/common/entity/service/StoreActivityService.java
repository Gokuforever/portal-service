package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.StoreActivity;
import com.sorted.common.enums.Activity;
import com.sorted.common.helper.AggregationFilter;
import com.sorted.common.repository.mongo.StoreActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class StoreActivityService extends GenericEntityServiceImpl<String, StoreActivity, StoreActivityRepository> {
    @Override
    protected Class<StoreActivityRepository> getRepoClass() {
        return StoreActivityRepository.class;
    }

    @Override
    protected void validateBeforeCreate(StoreActivity inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeUpdate(String id, StoreActivity inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }

    public void openStore(String storeId, String cudBy) {
        openOrCloseStore(storeId, Activity.OPEN_STORE, cudBy);
    }

    public void closeStore(String storeId, String cudBy) {
        openOrCloseStore(storeId, Activity.CLOSE_STORE, cudBy);
    }

    public void autoOpenStore(String storeId, String cudBy) {
        openOrCloseStore(storeId, Activity.AUTO_OPEN_STORE, cudBy);
    }

    public void autoCloseStore(String storeId, String cudBy) {
        openOrCloseStore(storeId, Activity.AUTO_CLOSE_STORE, cudBy);
    }

    public boolean isStoreOperational(String storeId) {
        AggregationFilter.SEFilter filter = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filter.addClause(AggregationFilter.WhereClause.eq(StoreActivity.Fields.store_id, storeId));
        filter.addClause(AggregationFilter.WhereClause.in(StoreActivity.Fields.activity_id, Arrays.asList(Activity.OPEN_STORE.getId(), Activity.AUTO_OPEN_STORE.getId())));
        filter.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        StoreActivity storeActivity = this.repoFindOne(filter);
        return storeActivity != null;
    }

    public List<String> getOperationalStores(List<String> storeIds) {
        AggregationFilter.SEFilter filter = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filter.addClause(AggregationFilter.WhereClause.in(StoreActivity.Fields.store_id, storeIds));
        filter.addClause(AggregationFilter.WhereClause.in(StoreActivity.Fields.activity_id, Arrays.asList(Activity.OPEN_STORE.getId(), Activity.AUTO_OPEN_STORE.getId())));
        filter.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<StoreActivity> storeActivities = this.repoFind(filter);
        if (CollectionUtils.isEmpty(storeActivities)) return new ArrayList<>();
        return storeActivities.stream().map(StoreActivity::getStore_id).toList();
    }

    private void openOrCloseStore(String storeId, Activity activity, String cudBy) {
        AggregationFilter.SEFilter filter = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filter.addClause(AggregationFilter.WhereClause.eq(StoreActivity.Fields.store_id, storeId));
        filter.addClause(AggregationFilter.WhereClause.in(StoreActivity.Fields.activity_id, Arrays.asList(Activity.OPEN_STORE.getId(), Activity.CLOSE_STORE.getId(), Activity.AUTO_OPEN_STORE.getId(), Activity.AUTO_CLOSE_STORE.getId())));
        filter.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<StoreActivity> storeActivities = this.repoFind(filter);
        if (!CollectionUtils.isEmpty(storeActivities)) {
            storeActivities.forEach(storeActivity -> {
                this.deleteOne(storeActivity.getId(), cudBy);
            });
        }

        StoreActivity storeActivity = new StoreActivity();
        storeActivity.setStore_id(storeId);
        storeActivity.setActivity_id(activity.getId());
        this.create(storeActivity, cudBy);
    }
}
