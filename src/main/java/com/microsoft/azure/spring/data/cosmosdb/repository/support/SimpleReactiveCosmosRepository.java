/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */
package com.microsoft.azure.spring.data.cosmosdb.repository.support;

import com.azure.data.cosmos.CosmosContainerResponse;
import com.azure.data.cosmos.PartitionKey;
import com.microsoft.azure.spring.data.cosmosdb.core.ReactiveCosmosOperations;
import com.microsoft.azure.spring.data.cosmosdb.core.query.Criteria;
import com.microsoft.azure.spring.data.cosmosdb.core.query.CriteriaType;
import com.microsoft.azure.spring.data.cosmosdb.core.query.DocumentQuery;
import com.microsoft.azure.spring.data.cosmosdb.repository.ReactiveCosmosRepository;
import org.reactivestreams.Publisher;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.Serializable;

public class SimpleReactiveCosmosRepository<T, K extends Serializable> implements ReactiveCosmosRepository<T, K> {

    private final CosmosEntityInformation<T, K> entityInformation;
    private final ReactiveCosmosOperations cosmosOperations;

    public SimpleReactiveCosmosRepository(CosmosEntityInformation<T, K> metadata,
                                          ApplicationContext applicationContext) {
        this.cosmosOperations = applicationContext.getBean(ReactiveCosmosOperations.class);
        this.entityInformation = metadata;

        createCollectionIfNotExists();
    }

    public SimpleReactiveCosmosRepository(CosmosEntityInformation<T, K> metadata,
                                          ReactiveCosmosOperations reactiveCosmosOperations) {
        this.cosmosOperations = reactiveCosmosOperations;
        this.entityInformation = metadata;

        createCollectionIfNotExists();
    }

    private CosmosContainerResponse createCollectionIfNotExists() {
        return this.cosmosOperations.createCollectionIfNotExists(this.entityInformation).block();
    }

    @Override
    public Flux<T> findAll(Sort sort) {
        Assert.notNull(sort, "Sort must not be null!");

        final DocumentQuery query =
            new DocumentQuery(Criteria.getInstance(CriteriaType.ALL)).with(sort);

        return cosmosOperations.find(query, entityInformation.getJavaType(),
            entityInformation.getCollectionName());
    }

    @Override
    public <S extends T> Mono<S> save(S entity) {

        Assert.notNull(entity, "Entity must not be null!");

        if (entityInformation.isNew(entity)) {
            return cosmosOperations.insert(entityInformation.getCollectionName(),
                entity,
                createKey(entityInformation.getPartitionKeyFieldValue(entity)));
        } else {
            return cosmosOperations.upsert(entityInformation.getCollectionName(),
                entity, createKey(entityInformation.getPartitionKeyFieldValue(entity)));
        }
    }

    @Override
    public <S extends T> Flux<S> saveAll(Iterable<S> entities) {

        Assert.notNull(entities, "The given Iterable of entities must not be null!");

        return Flux.fromIterable(entities).flatMap(this::save);
    }

    @Override
    public <S extends T> Flux<S> saveAll(Publisher<S> entityStream) {

        Assert.notNull(entityStream, "The given Publisher of entities must not be null!");

        return Flux.from(entityStream).flatMap(this::save);
    }

    @Override
    public Mono<T> findById(K id) {
        Assert.notNull(id, "The given id must not be null!");
        return cosmosOperations.findById(entityInformation.getCollectionName(), id,
            entityInformation.getJavaType());
    }

    @Override
    public Mono<T> findById(Publisher<K> publisher) {
        Assert.notNull(publisher, "The given id must not be null!");

        return Mono.from(publisher).flatMap(
            id -> cosmosOperations.findById(entityInformation.getCollectionName(),
                id, entityInformation.getJavaType()));
    }

    @Override
    public Mono<T> findById(K id, PartitionKey partitionKey) {
        Assert.notNull(id, "The given id must not be null!");
        return cosmosOperations.findById(id,
            entityInformation.getJavaType(), partitionKey);
    }

    @Override
    public Mono<Boolean> existsById(K id) {
        Assert.notNull(id, "The given id must not be null!");

        return cosmosOperations.existsById(id, entityInformation.getJavaType(),
            entityInformation.getCollectionName());
    }

    @Override
    public Mono<Boolean> existsById(Publisher<K> publisher) {
        Assert.notNull(publisher, "The given id must not be null!");

        return Mono.from(publisher).flatMap(id -> cosmosOperations.existsById(id,
            entityInformation.getJavaType(),
            entityInformation.getCollectionName()));
    }

    @Override
    public Flux<T> findAll() {
        return cosmosOperations.findAll(entityInformation.getCollectionName(),
            entityInformation.getJavaType());
    }

    @Override
    public Flux<T> findAllById(Iterable<K> ids) {
        Assert.notNull(ids, "Iterable ids should not be null");
        throw new UnsupportedOperationException();
    }

    @Override
    public Flux<T> findAllById(Publisher<K> ids) {
        Assert.notNull(ids, "The given Publisher of Id's must not be null!");
        throw new UnsupportedOperationException();
    }

    @Override
    public Mono<Long> count() {
        return cosmosOperations.count(entityInformation.getCollectionName());
    }

    @Override
    public Mono<Void> deleteById(K id) {
        Assert.notNull(id, "The given id must not be null!");

        return cosmosOperations.deleteById(entityInformation.getCollectionName(), id, null);
    }

    @Override
    public Mono<Void> deleteById(Publisher<K> publisher) {
        Assert.notNull(publisher, "Id must not be null!");

        return Mono.from(publisher).flatMap(id -> cosmosOperations.deleteById(entityInformation.getCollectionName(),
            id, null)).then();
    }

    @Override
    public Mono<Void> deleteById(K id, PartitionKey partitionKey) {
        Assert.notNull(id, "Id must not be null!");
        Assert.notNull(partitionKey, "PartitionKey must not be null!");

        return cosmosOperations.deleteById(entityInformation.getCollectionName(), id, partitionKey);

    }

    @Override
    public Mono<Void> delete(@NonNull T entity) {
        Assert.notNull(entity, "entity to be deleted must not be null!");

        final Object id = entityInformation.getId(entity);
        return cosmosOperations.deleteById(entityInformation.getCollectionName(),
            id,
            createKey(entityInformation.getPartitionKeyFieldValue(entity)));
    }

    @Override
    public Mono<Void> deleteAll(Iterable<? extends T> entities) {
        Assert.notNull(entities, "The given Iterable of entities must not be null!");

        return Flux.fromIterable(entities).flatMap(this::delete).then();
    }

    @Override
    public Mono<Void> deleteAll(Publisher<? extends T> entityStream) {

        Assert.notNull(entityStream, "The given Publisher of entities must not be null!");

        return Flux.from(entityStream)//
                   .map(entityInformation::getRequiredId)//
                   .flatMap(this::deleteById)//
                   .then();
    }

    @Override
    public Mono<Void> deleteAll() {
        return cosmosOperations.deleteAll(entityInformation.getCollectionName(),
            entityInformation.getPartitionKeyFieldName());
    }

    private PartitionKey createKey(String partitionKeyValue) {
        if (StringUtils.isEmpty(partitionKeyValue)) {
            return null;
        }
        return new PartitionKey(partitionKeyValue);
    }

}
