package org.springframework.data.simpledb.query;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.simpledb.core.SimpleDbOperations;
import org.springframework.data.simpledb.core.entity.field.FieldType;
import org.springframework.data.simpledb.core.entity.field.FieldTypeIdentifier;
import org.springframework.data.simpledb.query.executions.CollectionExecution;
import org.springframework.data.simpledb.query.executions.CountExecution;
import org.springframework.data.simpledb.query.executions.PartialCollectionExecution;
import org.springframework.data.simpledb.query.executions.PartialCollectionFieldExecution;
import org.springframework.data.simpledb.query.executions.PartialListOfOneFiledExecution;
import org.springframework.data.simpledb.query.executions.PartialSetOfOneFiledExecution;
import org.springframework.data.simpledb.query.executions.PartialSingleResultExecution;
import org.springframework.data.simpledb.query.executions.SimpleDbQueryExecution;
import org.springframework.data.simpledb.query.executions.SingleResultExecution;
import org.springframework.data.simpledb.util.QueryUtils;
import org.springframework.util.Assert;

/**
 * {@link RepositoryQuery} implementation that inspects a {@link SimpleDbQueryMethod} for the existence of an {@link org.springframework.data.simpledb.annotation.Query} annotation and provides
 * implementations based on query method information.
 */
public class SimpleDbRepositoryQuery implements RepositoryQuery {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleDbRepositoryQuery.class);
    private final SimpleDbQueryMethod method;
    private final SimpleDbOperations<?, Serializable> simpledbOperations;

    public SimpleDbRepositoryQuery(SimpleDbQueryMethod method, SimpleDbOperations<?, Serializable> simpledbOperations) {
        this.method = method;
        this.simpledbOperations = simpledbOperations;
    }

    @Override
    public Object execute(Object[] parameters) {
        return getExecution().execute(this, parameters);
    }

    @Override
    public QueryMethod getQueryMethod() {
        return method;
    }

    /**
     * Creates a {@link RepositoryQuery} from the given {@link org.springframework.data.repository.query.QueryMethod} that is potentially annotated with
     * {@link org.springframework.data.simpledb.annotation.Query}.
     *
     * @param queryMethod
     * @return the {@link RepositoryQuery} derived from the annotation or {@code null} if no annotation found.
     */
    public static RepositoryQuery fromQueryAnnotation(SimpleDbQueryMethod queryMethod, SimpleDbOperations<?, Serializable> simpleDbOperations) {
        LOGGER.debug("Looking up query for method {}", queryMethod.getName());
        return queryMethod.getAnnotatedQuery() == null ? null : new SimpleDbRepositoryQuery(queryMethod, simpleDbOperations);
    }

    public String getAnnotatedQuery() {
        return method.getAnnotatedQuery();
    }

    protected SimpleDbQueryExecution getExecution() {
        //TODO fix this
        //return query and method
        String query = method.getAnnotatedQuery();
        assertNotHavingNestedQueryParameters(query);
        if (QueryUtils.isCountQuery(query)) {
            return new CountExecution(simpledbOperations);
        } else if (method.isCollectionQuery()) {
            if (isReturnedTypeListOfDomainClass()) {
                return new CollectionExecution(simpledbOperations);
            } else if (QueryUtils.getQueryPartialFieldNames(query).size() > 1) {
                return new PartialCollectionExecution(simpledbOperations);
            } else {
                if (isGenericResultType()) {
                    return new PartialCollectionExecution(simpledbOperations);
                } else if (isSingleCollectionField(query)) {
                    return new PartialCollectionFieldExecution(simpledbOperations);
                } else if (List.class.isAssignableFrom(method.getReturnType())) {
                    return new PartialListOfOneFiledExecution(simpledbOperations);
                } else if (Set.class.isAssignableFrom(method.getReturnType())) {
                    return new PartialSetOfOneFiledExecution(simpledbOperations);
                } else {
                    throw new IllegalArgumentException("Wrong return type for query: " + query);
                }
            }
        } else if (method.isQueryForEntity()) {
            return new SingleResultExecution(simpledbOperations);
        } else if (method.isPageQuery()) {
            throw new IllegalArgumentException("Not implemented");
        } else if (method.isModifyingQuery()) {
            throw new IllegalArgumentException("Not implemented");
        } else {
            return new PartialSingleResultExecution(simpledbOperations);
        }
    }

    private boolean isSingleCollectionField(String query) {
        final Class<?> domainClass = method.getDomainClass();
        List<String> attributesFromQuery = QueryUtils.getQueryPartialFieldNames(query);
        Assert.isTrue(attributesFromQuery.size() == 1, "Query doesn't contain only one attribute in selected clause :" + query);
        String attributeName = attributesFromQuery.get(0);
        try {
            Field field = domainClass.getDeclaredField(attributeName);
            Class<?> type = field.getType();
            if (Collection.class.isAssignableFrom(type)) {
                ParameterizedType returnType = method.getGenericReturnType();
                Type returnedGenericType = returnType.getActualTypeArguments()[0];
                if (!(returnedGenericType instanceof ParameterizedType)) {
                    return true;
                }
            }
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("Filed doesn't exist in entity :" + query, e);
        }
        return false;
    }

    private boolean isGenericResultType() {
        ParameterizedType returnType = method.getGenericReturnType();
        Type returnedGenericType = returnType.getActualTypeArguments()[0];

        if (returnedGenericType instanceof ParameterizedType) {
            ParameterizedType secondGenericType = (ParameterizedType) returnedGenericType;
            Class<?> rowType = (Class<?>) secondGenericType.getRawType();
            if (!List.class.isAssignableFrom(rowType)) {
                return false;
            }
            Class<?> genericObject = (Class<?>) secondGenericType.getActualTypeArguments()[0];

            if (genericObject.equals(Object.class)) {
                return true;
            }
        }
        return false;
    }

    private boolean isReturnedTypeListOfDomainClass() {
        ParameterizedType returnType = method.getGenericReturnType();
        Type returnedGenericType = returnType.getActualTypeArguments()[0];

        return returnedGenericType.equals(method.getDomainClass());
    }

    private void assertNotHavingNestedQueryParameters(String query) {
        List<String> attributesFromQuery = QueryUtils.getQueryPartialFieldNames(query);
        final Class<?> domainClass = method.getDomainClass();
        for (String attribute : attributesFromQuery) {
            try {
                Field field = domainClass.getDeclaredField(attribute);
                if (FieldTypeIdentifier.isOfType(field, FieldType.NESTED_ENTITY)) {
                    throw new IllegalArgumentException("Invalid query parameter :" + attribute + " is nested object");
                }
            } catch (NoSuchFieldException e) {
                //might be a count or something else
            }
        }
    }
}
