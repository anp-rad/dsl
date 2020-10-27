package com.aspirecsl.dsl.db;

import java.util.List;
import java.util.Optional;

import com.aspirecsl.dsl.common.With;
import com.aspirecsl.dsl.db.AbstractTypedQueryParamsBuilder.TypedQueryParamsBuilder;
import com.aspirecsl.dsl.db.AbstractTypedQueryParamsBuilder.TypedQueryParamsSubmitter;

/**
 * Provides common functions to interact with an entity
 *
 * @param <E> the type of the entity
 * @author anoopr
 */
public class ResourceManager<E> {

    /**
     * the JPA repository implementation of the entity managed by this <tt>ResourceManager</tt>
     **/
    private final BaseRepository<E> repository;

    /**
     * Creates a new instance with the supplied values
     *
     * @param repository the JPA repository implementation of the entity managed by this <tt>ResourceManager</tt>
     */
    public ResourceManager(BaseRepository<E> repository) {
        this.repository = repository;
    }

    /**
     * Returns a <tt>TypedQueryParamsBuilder</tt> to build the query parameters for the entity managed by this <tt>ResourceManager</tt>
     *
     * @return a <tt>TypedQueryParamsBuilder</tt> to build the query parameters for the entity managed by this <tt>ResourceManager</tt>
     */
    public TypedQueryParamsBuilder<E> queryParamsBuilder() {
        return TypedQueryParamsBuilder.create();
    }

    /**
     * Returns a <tt>With</tt> object containing the <em>EntityUpdater</em> for the entity managed by this <tt>ResourceManager</tt>
     *
     * @return a <tt>With</tt> object containing the <em>EntityUpdater</em> for the entity managed by this <tt>ResourceManager</tt>
     */
    public With<SimpleEntityUpdater<E>> update(TypedQueryParams<E> queryParams) {
        return () -> SimpleEntityUpdater.of(repository, queryParams);
    }

    /**
     * Returns a <tt>With</tt> object containing the <tt>TypedQueryParamsSubmitter</tt> which can be used to build a
     * <tt>TypedQueryParams</tt> object to delete matching entries in the database.
     *
     * @return a <tt>With</tt> object containing the <tt>TypedQueryParamsSubmitter</tt> which can be used to build a
     * <tt>TypedQueryParams</tt> object to delete matching entries in the database.
     */
    public With<TypedQueryParamsSubmitter<E, Integer>> delete() {
        return () -> TypedQueryParamsSubmitter.create(repository::deleteUsing);
    }

    /**
     * Returns a <tt>With</tt> object containing the <tt>TypedQueryParamsSubmitter</tt> which can be used to build a
     * <tt>TypedQueryParams</tt> object to find a matching entry in the database.
     *
     * @return a <tt>With</tt> object containing the <tt>TypedQueryParamsSubmitter</tt> which can be used to build a
     * <tt>TypedQueryParams</tt> object to find a matching entry in the database.
     */
    public With<TypedQueryParamsSubmitter<E, Optional<E>>> findOne() {
        return () -> TypedQueryParamsSubmitter.create(q -> Optional.ofNullable(repository.findOneUsing(q)));
    }

    /**
     * Returns a <tt>With</tt> object containing the <tt>TypedQueryParamsSubmitter</tt> which can be used to build a
     * <tt>TypedQueryParams</tt> object to find a list of matching entries in the database.
     *
     * @return a <tt>With</tt> object containing the <tt>TypedQueryParamsSubmitter</tt> which can be used to build a
     * <tt>TypedQueryParams</tt> object to find a list of matching entries in the database.
     */
    public With<TypedQueryParamsSubmitter<E, List<E>>> findAll() {
        return () -> TypedQueryParamsSubmitter.create(repository::findAllUsing);
    }

    /**
     * Returns a <tt>With</tt> object containing the <tt>TypedQueryParamsSubmitter</tt> which can be used to build a
     * <tt>TypedQueryParams</tt> object to find the first <em>(a.k.a top)</em> matching entry in the database.
     *
     * @return a <tt>With</tt> object containing the <tt>TypedQueryParamsSubmitter</tt> which can be used to build a
     * <tt>TypedQueryParams</tt> object to find the first <em>(a.k.a top)</em> matching entry in the database.
     */
    public With<TypedQueryParamsSubmitter<E, Optional<E>>> findTop() {
        return () -> TypedQueryParamsSubmitter.create(q -> Optional.ofNullable(repository.findTopUsing(q)));
    }

    /**
     * Returns a <tt>With</tt> object containing the <tt>TypedQueryParamsSubmitter</tt> which can be used to build a
     * <tt>TypedQueryParams</tt> object to find the first <em>(a.k.a top)</em> <tt>count</tt> matching entries in the database.
     *
     * @param count the number of entities to retrieve from the database
     * @return a <tt>With</tt> object containing the <tt>TypedQueryParamsSubmitter</tt> which can be used to build a
     * <tt>TypedQueryParams</tt> object to find the first <em>(a.k.a top)</em> <tt>count</tt> matching entries in the database.
     */
    public With<TypedQueryParamsSubmitter<E, List<E>>> findTop(int count) {
        return () -> TypedQueryParamsSubmitter.create(q -> repository.findTopUsing(count, q));
    }

    /**
     * Deletes all the entity entries matching the params in the the given <tt>queryParams</tt>
     *
     * @param params the query params to select the entity or entities
     * @return the count of deleted entries
     */
    public int delete(TypedQueryParams<E> params) {
        return repository.deleteUsing(params);
    }

    /**
     * Returns an <tt>Optional</tt> describing the record from the database entity matching the params in the given
     * <tt>matcher</tt>; or an <tt>empty Optional</tt> if no record matches the params in the given <tt>queryParams</tt> object.
     *
     * @param params the query params to select the entity
     * @return an <tt>Optional</tt> describing the record from the database entity matching the params in the given
     * <tt>matcher</tt>; or an <tt>empty Optional</tt> if no record matches the params in the given <tt>queryParams</tt> object.
     */
    public Optional<E> findOne(TypedQueryParams<E> params) {
        return Optional.ofNullable(repository.findOneUsing(params));
    }

    /**
     * Returns a list of records from the database entity matching the params in the given <tt>params</tt> object;
     * or <tt>null</tt> if no record matches the params in the given <tt>params</tt> object.
     *
     * @param params the query params to select the entities
     * @return a list of records from the database entity matching the params in the given <tt>params</tt> object;
     * or <tt>null</tt> if no record matches the params in the given <tt>params</tt> object.
     */
    public List<E> findAll(TypedQueryParams<E> params) {
        return repository.findAllUsing(params);
    }
}
