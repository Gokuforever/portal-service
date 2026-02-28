package com.sorted.common.helper;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.sorted.common.config.StaticMongoAccessor;
import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.enums.Operators;
import com.sorted.common.enums.ResponseCode;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import com.sorted.common.helper.AggregationFilter.*;
import lombok.NonNull;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public interface BaseMongoRepository<K, T extends BaseMongoEntity<K>>
        extends BaseRepository<T, K>, MongoRepository<T, K> {

    static Logger logger = LoggerFactory.getLogger(BaseMongoRepository.class);

    default Class<T> getEntityType() {
        throw new UnsupportedOperationException("This method must be overridden in the child interface.");
    }

    @Override
    default List<T> repoFindAll() {
        return this.findAll();
    }

    @Override
    default T create(@NonNull T obj, String cudby) {
        obj.setBeforeCreate(cudby);
        return this.insert(obj);
    }

    @Override
    default List<T> bulkCreate(@NonNull List<T> list, String cudby) {
        List<T> newList = new ArrayList<>();
        for (T t : list) {
            t.setBeforeCreate(cudby);
            newList.add(t);
        }
        return this.insert(newList);
    }

    @Override
    default T update(K id, T obj, String cudby) {
        Optional<T> optional = this.findById(id);
        if (optional.isEmpty()) {
            throw new CustomIllegalArgumentsException(ResponseCode.MISSING_ID);
        }
        obj.setBeforeModification(cudby);
        return this.save(obj);
    }

    @Override
    default T upsert(K id, T obj, String cud_by) {
        if (id == null) {
            return this.create(obj, cud_by);
        }
        return update(id, obj, cud_by);
    }

    @Override
    default long totalCount() {
        return this.count();
    }

    @Override
    default T repoFindOne(SEFilter f) {
        Query query = buildQuery(f);
        logger.info("query:: {}, entity:: {}", query, getEntityType());
        return StaticMongoAccessor.MONGO_TEMPLATE.findOne(query, getEntityType());
    }

    @Override
    default List<T> repoFind(SEFilter f) {
        Query query = buildQuery(f);
        logger.info("query:: {}, entity:: {}", query, getEntityType());
        return StaticMongoAccessor.MONGO_TEMPLATE.find(query, getEntityType());
    }

    @Override
    default long countByFilter(SEFilter f) {
        Query query = buildQuery(f);
        logger.info("query:: {}, entity:: {}", query, getEntityType());
        return StaticMongoAccessor.MONGO_TEMPLATE.count(query, getEntityType());
    }

    @NotNull
    @Override
    default Optional<T> findById(@NotNull K k) {
        SEFilter f = new SEFilter(SEFilterType.AND);
        f.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, k));
        f.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        return Optional.ofNullable(this.repoFindOne(f));
    }

    ;

    @Override
    default void deleteOne(K id, String cud_by) {
        Optional<T> optional = this.findById(id);
        if (optional.isEmpty()) {
            return;
        }
        T t = optional.get();
        t.setDeleted(true);
        t.setBeforeModification(cud_by);
        this.save(t);
    }

    default List<T> random(SEFilter f, long count) {
        Query query = buildQuery(f);
        logger.info("query:: {}, entity:: {}", query, getEntityType());
        // Get the native MongoDB collection
        MongoCollection<Document> collection = StaticMongoAccessor.MONGO_TEMPLATE.getCollection(
                StaticMongoAccessor.MONGO_TEMPLATE.getCollectionName(this.getEntityType())
        );

        // Create aggregation pipeline
        List<Document> pipeline = new ArrayList<>();

        // Add $match stage if criteria is provided
        Document matchDoc = query.getQueryObject();
        pipeline.add(new Document("$match", matchDoc));
        pipeline.add(new Document("$sample", new Document("size", count)));

        // Execute aggregation
        AggregateIterable<Document> result = collection.aggregate(pipeline);
        // Convert results to entity objects
        List<T> entities = new ArrayList<>();
        for (Document doc : result) {
            T entity = StaticMongoAccessor.MONGO_TEMPLATE.getConverter().read(this.getEntityType(), doc);
            entities.add(entity);
        }
        return entities;
    }

    private static Query buildQuery(SEFilter filter) {
        Criteria criteria = buildCriteria(filter);

        Query query = new Query(criteria);

        if (filter.getOrderBy() != null) {
            query.with(buildSort(filter.getOrderBy()));
        }

        if (filter.getSelection() != null && !filter.getSelection().isEmpty()) {
            query.fields().include(filter.getSelection().toArray(new String[0]));
        }
        if (filter.getPagination() != null) {
            Pageable pageable = PageRequest.of(filter.getPagination().getPage(), filter.getPagination().getSize());
            query.with(pageable);
        }
        return query;
    }

    private static Criteria buildCriteria(SEFilter filter) {
        List<WhereClause> clauses = filter.getClause();

        SEFilterType baseType = filter.getType();

        Criteria criteria = new Criteria();
        Criteria sub_query_criteria = new Criteria();
        List<Criteria> baseQueryCriterias = new ArrayList<>();
        List<Criteria> nodeCriterias = new ArrayList<>();

        if (!CollectionUtils.isEmpty(clauses)) {
            baseQueryCriterias = clauses.stream().map(BaseMongoRepository::buildWhereClauseCriteria)
                    .collect(Collectors.toList());
        }
        if (!CollectionUtils.isEmpty(filter.getNodes())) {
            nodeCriterias = filter.getNodes().stream().map(BaseMongoRepository::buildCriteria).toList();

        }
        Criteria[] arrComb = null;
        if (!nodeCriterias.isEmpty() && !baseQueryCriterias.isEmpty()) {
            if (nodeCriterias.size() > 1) {
//				SEFilterType subquery_type = filter.getSubquery_type() == null ? SEFilterType.AND
//						: filter.getSubquery_type();
                Criteria[] subQueriesComb = nodeCriterias.toArray(new Criteria[0]);
//				if (subquery_type.equals(SEFilterType.AND)) {
//					sub_query_criteria.andOperator(subQueriesComb);
//				} else if (subquery_type.equals(SEFilterType.OR)) {
                sub_query_criteria.orOperator(subQueriesComb);
//				}
                baseQueryCriterias.add(sub_query_criteria);
            } else {
                baseQueryCriterias.addAll(nodeCriterias);
            }
            arrComb = baseQueryCriterias.toArray(new Criteria[0]);
        } else if (!baseQueryCriterias.isEmpty()) {
            arrComb = baseQueryCriterias.toArray(new Criteria[0]);
        } else if (!nodeCriterias.isEmpty()) {
            arrComb = nodeCriterias.toArray(new Criteria[0]);
        } else {
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
        if (baseType.equals(SEFilterType.AND)) {
            criteria.andOperator(arrComb);
        } else if (baseType.equals(SEFilterType.OR)) {
            criteria.orOperator(arrComb);
        }
        return criteria;
    }

    private static Criteria buildCriteria(SEFilterNode node) {
        Criteria criteria = new Criteria();

        if (!CollectionUtils.isEmpty(node.getClause())) {
            List<Criteria> whereCriterias = node.getClause().stream().map(BaseMongoRepository::buildWhereClauseCriteria)
                    .toList();

            if (node.getType() == SEFilterType.AND) {
                criteria.andOperator(whereCriterias.toArray(new Criteria[0]));
            } else if (node.getType() == SEFilterType.OR) {
                criteria.orOperator(whereCriterias.toArray(new Criteria[0]));
            }
        }
        return criteria;
    }

    private static Criteria buildWhereClauseCriteria(WhereClause clause) {
        Operators relation = clause.getOperator();
        switch (relation) {
            case LIKE:
                String valueL = Pattern.quote(clause.getValue());
                String regexL = String.format(".*(%s).*", valueL);
                return new Criteria(clause.getField()).regex(regexL, "i");
            case NOT_LIKE:
                String valueNL = Pattern.quote(clause.getValue());
                String regexNL = String.format(".*(%s).*", valueNL);
                return new Criteria(clause.getField()).not().regex(regexNL, "i");
            case EQUALS:
                return new Criteria(clause.getField()).is(clause.getValueAsObject());
            case NOT_EQUALS:
                return new Criteria(clause.getField()).ne(clause.getValueAsObject());
            case IN:
                return new Criteria(clause.getField()).in(clause.getValueList());
            case NIN:
                return new Criteria(clause.getField()).nin(clause.getValueList());
            case ALL:
                return new Criteria(clause.getField()).all(clause.getValueList());
            case GT:
                return new Criteria(clause.getField()).gt(clause.getValueAsObject());
            case LT:
                return new Criteria(clause.getField()).lt(clause.getValueAsObject());
            case GTE:
                return new Criteria(clause.getField()).gte(clause.getValueAsObject());
            case LTE:
                return new Criteria(clause.getField()).lte(clause.getValueAsObject());
            case ELEMMATCH_IN:
                String keyName = clause.getField();
                Criteria elemMatchCriteria = new Criteria();
                Map<String, Object> elemMap = clause.getElemMap();
                for (Map.Entry<String, Object> entry : elemMap.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (value instanceof List<?>) {
                        // Use 'in' for list values
                        elemMatchCriteria = elemMatchCriteria.and(key).in((List<?>) value);
                    } else {
                        // Use 'is' for single values
                        elemMatchCriteria = elemMatchCriteria.and(key).is(value);
                    }
                }
                return new Criteria(keyName).elemMatch(elemMatchCriteria);
            case IS_NULL:
                return new Criteria(clause.getField()).isNull();
            case IS_NOT_NULL:
                return new Criteria(clause.getField()).ne(null);
            case IS_EMPTY:
                return new Criteria(clause.getField()).exists(true).is(Collections.emptyList());
            case IS_NOT_EMPTY:
                return new Criteria(clause.getField()).exists(true).ne(Collections.emptyList());
            default:
                return null;
        }

    }

    private static Sort buildSort(OrderBy orderBy) {
        Sort.Direction direction = orderBy.getType() == SortOrder.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, orderBy.getKey());
    }

}
