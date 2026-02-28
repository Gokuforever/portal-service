package com.sorted.common.config;

import com.sorted.common.entity.mongo.BaseMongoEntity;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.util.ClassUtils;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Configuration
@EnableAsync
public class MongoIndexConfig {

    private static final Logger logger = LoggerFactory.getLogger(MongoIndexConfig.class);
    private static final String BASE_PACKAGE = "com.sorted";

    @Autowired
    private MongoTemplate mongoTemplate;

    @PostConstruct
    public void initIndexes() {
        try {
            // Find all classes that extend BaseMongoEntity
            ClassPathScanningCandidateComponentProvider scanner =
                    new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AssignableTypeFilter(BaseMongoEntity.class));

            // Collect all entity classes first
            var entityClasses = scanner.findCandidateComponents(BASE_PACKAGE)
                    .stream()
                    .map(beanDefinition -> {
                        try {
                            return ClassUtils.forName(
                                    Objects.requireNonNull(beanDefinition.getBeanClassName()),
                                    ClassUtils.getDefaultClassLoader()
                            );
                        } catch (ClassNotFoundException e) {
                            logger.error("Failed to load class: {}", beanDefinition.getBeanClassName(), e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();

            // Process indexing in parallel
            CompletableFuture.allOf(
                    entityClasses.stream()
                            .map(this::ensureIndexesAsync)
                            .toArray(CompletableFuture[]::new)
            ).join();

            logger.info("Completed parallel index creation for {} entities", entityClasses.size());
        } catch (Exception e) {
            logger.error("Failed to initialize MongoDB indexes", e);
        }
    }

    @Async
    public CompletableFuture<Void> ensureIndexesAsync(Class<?> entityClass) {
        return CompletableFuture.runAsync(() -> {
            try {
                IndexOperations indexOps = mongoTemplate.indexOps(entityClass);

                // Common indexes for all collections
                CompletableFuture.allOf(
                        CompletableFuture.runAsync(() ->
                                indexOps.ensureIndex(new Index()
                                        .on("creation_date", Sort.Direction.DESC)
                                        .background())),
                        CompletableFuture.runAsync(() ->
                                indexOps.ensureIndex(new Index()
                                        .on("modification_date", Sort.Direction.DESC)
                                        .background())),
                        CompletableFuture.runAsync(() ->
                                indexOps.ensureIndex(new Index()
                                        .on("deleted", Sort.Direction.ASC)
                                        .background()))
                ).join();

                logger.info("Created indexes for entity: {}", entityClass.getName());
                logger.debug("Existing indexes for {}: {}",
                        entityClass.getSimpleName(),
                        indexOps.getIndexInfo());
            } catch (Exception e) {
                logger.error("Failed to create indexes for entity: {}", entityClass.getName(), e);
            }
        });
    }
} 