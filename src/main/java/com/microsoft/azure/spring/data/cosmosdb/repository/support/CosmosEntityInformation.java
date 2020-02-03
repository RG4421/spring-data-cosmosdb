/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.azure.spring.data.cosmosdb.repository.support;

import com.azure.data.cosmos.ExcludedPath;
import com.azure.data.cosmos.IncludedPath;
import com.azure.data.cosmos.IndexingMode;
import com.azure.data.cosmos.IndexingPolicy;
import com.microsoft.azure.spring.data.cosmosdb.Constants;
import com.microsoft.azure.spring.data.cosmosdb.common.Memoizer;
import com.microsoft.azure.spring.data.cosmosdb.core.mapping.Document;
import com.microsoft.azure.spring.data.cosmosdb.core.mapping.DocumentIndexingPolicy;
import com.microsoft.azure.spring.data.cosmosdb.core.mapping.PartitionKey;
import org.apache.commons.lang3.reflect.FieldUtils;

import org.json.JSONObject;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.repository.core.support.AbstractEntityInformation;
import org.springframework.lang.NonNull;
import org.springframework.util.ReflectionUtils;

import static com.microsoft.azure.spring.data.cosmosdb.common.ExpressionResolver.resolveExpression;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;


public class CosmosEntityInformation<T, ID> extends AbstractEntityInformation<T, ID> {

    private static Function<Class<?>, CosmosEntityInformation<?, ?>> ENTITY_INFO_CREATOR =
            Memoizer.memoize(CosmosEntityInformation::getCosmosEntityInformation);

    private static CosmosEntityInformation<?, ?> getCosmosEntityInformation(Class<?> domainClass) {
        return new CosmosEntityInformation<>(domainClass);
    }

    public static CosmosEntityInformation<?, ?> getInstance(Class<?> domainClass) {
        return ENTITY_INFO_CREATOR.apply(domainClass);
    }

    private Field id;
    private Field partitionKeyField;
    private Field versionField;
    private String collectionName;
    private Integer requestUnit;
    private Integer timeToLive;
    private IndexingPolicy indexingPolicy;
    private boolean autoCreateCollection;

    public CosmosEntityInformation(Class<T> domainClass) {
        super(domainClass);

        this.id = getIdField(domainClass);
        ReflectionUtils.makeAccessible(this.id);

        this.collectionName = getCollectionName(domainClass);
        this.partitionKeyField = getPartitionKeyField(domainClass);
        if (this.partitionKeyField != null) {
            ReflectionUtils.makeAccessible(this.partitionKeyField);
        }
        this.versionField = getVersionedField(domainClass);
        if (this.versionField != null) {
            ReflectionUtils.makeAccessible(this.versionField);
        }
        this.requestUnit = getRequestUnit(domainClass);
        this.timeToLive = getTimeToLive(domainClass);
        this.indexingPolicy = getIndexingPolicy(domainClass);
        this.autoCreateCollection = getIsAutoCreateCollection(domainClass);
    }

    @SuppressWarnings("unchecked")
    public ID getId(T entity) {
        return (ID) ReflectionUtils.getField(id, entity);
    }

    public Field getIdField() {
        return this.id;
    }

    @SuppressWarnings("unchecked")
    public Class<ID> getIdType() {
        return (Class<ID>) id.getType();
    }

    public String getCollectionName() {
        return this.collectionName;
    }

    public Integer getRequestUnit() {
        return this.requestUnit;
    }

    public Integer getTimeToLive() {
        return this.timeToLive;
    }

    @NonNull
    public IndexingPolicy getIndexingPolicy() {
        return this.indexingPolicy;
    }

    public boolean isVersioned() {
        return versionField != null;
    }

    public String getVersionFieldValue(T entity) {
        return versionField == null ? null : (String) ReflectionUtils.getField(versionField, entity);
    }

    public void setVersionFieldValue(T entity, String value) {
        if (versionField != null) {
            ReflectionUtils.setField(versionField, entity, value);
        }
    }

    public String getVersionFieldName() {
        return versionField == null ? null : versionField.getName();
    }

    public String getPartitionKeyFieldName() {
        if (partitionKeyField == null) {
            return null;
        } else {
            final PartitionKey partitionKey = partitionKeyField.getAnnotation(PartitionKey.class);
            return partitionKey.value().equals("") ? partitionKeyField.getName() : partitionKey.value();
        }
    }

    public String getPartitionKeyFieldValue(T entity) {
        return partitionKeyField == null ? null : (String) ReflectionUtils.getField(partitionKeyField, entity);
    }

    public boolean isAutoCreateCollection() {
        return autoCreateCollection;
    }

    private IndexingPolicy getIndexingPolicy(Class<?> domainClass) {
        final IndexingPolicy policy = new IndexingPolicy();

        policy.automatic(this.getIndexingPolicyAutomatic(domainClass));
        policy.indexingMode(this.getIndexingPolicyMode(domainClass));
        policy.setIncludedPaths(this.getIndexingPolicyIncludePaths(domainClass));
        policy.excludedPaths(this.getIndexingPolicyExcludePaths(domainClass));

        return policy;
    }

    private Field getIdField(Class<?> domainClass) {
        final Field idField;
        final List<Field> fields = FieldUtils.getFieldsListWithAnnotation(domainClass, Id.class);

        if (fields.isEmpty()) {
            idField = ReflectionUtils.findField(getJavaType(), Constants.ID_PROPERTY_NAME);
        } else if (fields.size() == 1) {
            idField = fields.get(0);
        } else {
            throw new IllegalArgumentException("only one field with @Id annotation!");
        }

        if (idField == null) {
            throw new IllegalArgumentException("domain should contain @Id field or field named id");
        } else if (idField.getType() != String.class
                && idField.getType() != Integer.class && idField.getType() != int.class) {
            throw new IllegalArgumentException("type of id field must be String or Integer");
        }

        return idField;
    }

    private String getCollectionName(Class<?> domainClass) {
        String customCollectionName = domainClass.getSimpleName();

        final Document annotation = domainClass.getAnnotation(Document.class);

        if (annotation != null && annotation.collection() != null && !annotation.collection().isEmpty()) {
            customCollectionName = resolveExpression(annotation.collection());
        }

        return customCollectionName;
    }

    private Field getPartitionKeyField(Class<?> domainClass) {
        Field partitionKey = null;

        final List<Field> fields = FieldUtils.getFieldsListWithAnnotation(domainClass, PartitionKey.class);

        if (fields.size() == 1) {
            partitionKey = fields.get(0);
        } else if (fields.size() > 1) {
            throw new IllegalArgumentException("Azure Cosmos DB supports only one partition key, " +
                    "only one field with @PartitionKey annotation!");
        }

        if (partitionKey != null && partitionKey.getType() != String.class) {
            throw new IllegalArgumentException("type of PartitionKey field must be String");
        }
        return partitionKey;
    }

    private Integer getRequestUnit(Class<?> domainClass) {
        Integer ru = Integer.parseInt(Constants.DEFAULT_REQUEST_UNIT);
        final Document annotation = domainClass.getAnnotation(Document.class);

        if (annotation != null && annotation.ru() != null && !annotation.ru().isEmpty()) {
            ru = Integer.parseInt(annotation.ru());
        }
        return ru;
    }

    private Integer getTimeToLive(Class<T> domainClass) {
        Integer ttl = Constants.DEFAULT_TIME_TO_LIVE;
        final Document annotation = domainClass.getAnnotation(Document.class);

        if (annotation != null) {
            ttl = annotation.timeToLive();
        }

        return ttl;
    }


    private Boolean getIndexingPolicyAutomatic(Class<?> domainClass) {
        Boolean isAutomatic = Boolean.valueOf(Constants.DEFAULT_INDEXINGPOLICY_AUTOMATIC);
        final DocumentIndexingPolicy annotation = domainClass.getAnnotation(DocumentIndexingPolicy.class);

        if (annotation != null) {
            isAutomatic = Boolean.valueOf(annotation.automatic());
        }

        return isAutomatic;
    }

    private IndexingMode getIndexingPolicyMode(Class<?> domainClass) {
        IndexingMode mode = Constants.DEFAULT_INDEXINGPOLICY_MODE;
        final DocumentIndexingPolicy annotation = domainClass.getAnnotation(DocumentIndexingPolicy.class);

        if (annotation != null) {
            mode = annotation.mode();
        }

        return mode;
    }

    private List<IncludedPath> getIndexingPolicyIncludePaths(Class<?> domainClass) {
        final List<IncludedPath> pathArrayList = new ArrayList<>();
        final DocumentIndexingPolicy annotation = domainClass.getAnnotation(DocumentIndexingPolicy.class);

        if (annotation == null || annotation.includePaths() == null || annotation.includePaths().length == 0) {
            return null; // Align the default value of IndexingPolicy
        }

        final String[] rawPaths = annotation.includePaths();

        for (final String path : rawPaths) {
            pathArrayList.add(new IncludedPath(path));
        }

        return pathArrayList;
    }

    private List<ExcludedPath> getIndexingPolicyExcludePaths(Class<?> domainClass) {
        final List<ExcludedPath> pathArrayList = new ArrayList<>();
        final DocumentIndexingPolicy annotation = domainClass.getAnnotation(DocumentIndexingPolicy.class);

        if (annotation == null || annotation.excludePaths().length == 0) {
            return null; // Align the default value of IndexingPolicy
        }

        final String[] rawPaths = annotation.excludePaths();
        for (final String path : rawPaths) {
            final JSONObject obj = new JSONObject(path);
            pathArrayList.add(new ExcludedPath().path(obj.get("path").toString()));
        }

        return pathArrayList;
    }

    private Field getVersionedField(Class<T> domainClass) {
        Field version = null;
        final List<Field> fields = FieldUtils.getFieldsListWithAnnotation(domainClass, Version.class);

        if (fields.size() == 1) {
            version = fields.get(0);
        } else if (fields.size() > 1) {
            throw new IllegalArgumentException("Azure Cosmos DB supports only one field with @Version annotation!");
        }

        if (version != null && version.getType() != String.class) {
            throw new IllegalArgumentException("type of Version field must be String");
        }
        return version;
    }

    private boolean getIsAutoCreateCollection(Class<T> domainClass) {
        final Document annotation = domainClass.getAnnotation(Document.class);

        boolean autoCreateCollection = Constants.DEFAULT_AUTO_CREATE_COLLECTION;
        if (annotation != null) {
            autoCreateCollection = annotation.autoCreateCollection();
        }

        return autoCreateCollection;
    }

}

