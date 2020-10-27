package com.aspirecsl.dsl.db;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import javax.persistence.criteria.CriteriaUpdate;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aspirecsl.dsl.db.AbstractTypedQueryParamsBuilder.TypedQueryParamsBuilder;

import static java.util.Collections.unmodifiableList;

/**
 * A base Spring Data JPA repository that provides some common functions.
 * <p>Only repositories that manage entities with <tt>ID</tt> type of <tt>Long</tt> can use this <tt>BaseRepository</tt>.
 * Entities with IDs of type other than <tt>Long</tt> cannot extend this <tt>BaseRepository</tt>. Generally speaking, this is not
 * an issue as all the PPS entities have system-generated IDs that are of type <tt>Long</tt>.
 *
 * @param <T> the type of the entity
 * @author anoopr
 */
public interface BaseRepository<T> extends JpaRepository<T, Long> {

    /**
     * Returns a record from the database entity matching the given <tt>queryParams</tt> object;
     * or <tt>null</tt> if no record matches the params in the given <tt>queryParams</tt> object.
     *
     * @param queryParams the object holding the parameters required to build a JPA <tt>CriteriaQuery</tt>
     *                    that retrieves record(s) from an entity
     * @return a record from the database entity matching the given <tt>queryParams</tt> object;
     * or <tt>null</tt> if no record matches the given <tt>queryParams</tt> object.
     * @see TypedQueryParams
     * @see AbstractTypedQueryParamsBuilder
     */
    T findOneUsing(TypedQueryParams<T> queryParams);

    /**
     * Returns a list of records from the database entity matching the given <tt>queryParams</tt> object;
     * or <tt>null</tt> if no record matches the params in the given <tt>queryParams</tt> object.
     *
     * @param queryParams the object holding the parameters required to build a JPA <tt>CriteriaQuery</tt>
     *                    that retrieves record(s) from an entity
     * @return a list of records from the database entity matching the given <tt>queryParams</tt> object;
     * or <tt>null</tt> if no record matches the given <tt>queryParams</tt> object.
     * @see TypedQueryParams
     * @see AbstractTypedQueryParamsBuilder
     */
    List<T> findAllUsing(TypedQueryParams<T> queryParams);

    /**
     * Returns the first <em>(a.k.a the top)</em> record from a database entity matching the given <tt>queryParams</tt> object;
     * or <tt>null</tt> if no record matches the params in the given <tt>queryParams</tt> object.
     * <p>When requesting the <tt>top</tt> record(s) of an entity it is usually required that the user specifies the ordering of
     * the entries retrieved from the database. Without an explicit order, the database may return entries in an undesirable order
     * resulting in the <tt>top</tt> entry to be one that is not expected by the user. To specify the order of the entries
     * obtained from the database, use one of the <tt>ascending(...)</tt> or the <tt>descending(...)</tt> methods in the
     * <tt>TypedQueryParamsBuilder</tt> object.
     *
     * @param queryParams the object holding the parameters required to build a JPA <tt>CriteriaQuery</tt>
     *                    that retrieves record(s) from an entity
     * @return the first <em>(a.k.a the top)</em> record from a database entity matching the given <tt>queryParams</tt> object;
     * or <tt>null</tt> if no record matches the params in the given <tt>queryParams</tt> object.
     * @see TypedQueryParams
     * @see AbstractTypedQueryParamsBuilder
     */
    default T findTopUsing(TypedQueryParams<T> queryParams) {
        final List<T> all = findAllUsing(queryParams);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * Returns the first <em>(a.k.a the top)</em> <tt>count</tt> records from a database entity matching the given
     * <tt>queryParams</tt> object; or an <tt>empty list</tt> if no record matches the given <tt>queryParams</tt> object.
     * <p>When requesting the <tt>top</tt> record(s) of an entity it is usually required that the user specifies the ordering of
     * the entries retrieved from the database. Without an explicit order, the database may return entries in an undesirable order
     * resulting in the <tt>top</tt> entry to be one that is not expected by the user. To specify the order of the entries
     * obtained from the database, use one of the <tt>ascending(...)</tt> or the <tt>descending(...)</tt> methods in the
     * {@link TypedQueryParamsBuilder} object.
     *
     * @param queryParams the object holding the parameters required to build a JPA <tt>CriteriaQuery</tt>
     *                    that retrieves record(s) from an entity
     * @return the first <em>(a.k.a the top)</em> <tt>count</tt> records from a database entity matching the given
     * <tt>queryParams</tt> object; or an <tt>empty list</tt> if no record matches the given <tt>queryParams</tt> object.
     * @see TypedQueryParams
     * @see AbstractTypedQueryParamsBuilder
     */
    default List<T> findTopUsing(int count, TypedQueryParams<T> queryParams) {
        final List<T> all = findAllUsing(queryParams);
        return all.isEmpty() ? Collections.emptyList() : unmodifiableList(all.subList(0, count));
    }

    /**
     * Deletes the entity entries matching the the given <tt>queryParams</tt>
     *
     * @param queryParams the filter params for the entity
     * @return the count of deleted entries
     * @see TypedQueryParams
     * @see AbstractTypedQueryParamsBuilder
     */
    int deleteUsing(TypedQueryParams<T> queryParams);

    /**
     * Updates the entity entries matching the the given <tt>queryParams</tt> using the supplied <tt>updates</tt>
     *
     * @param queryParams the filter params for the entity
     * @param updates     the list of updates to the entity
     * @return the count of updated entries
     * @see TypedQueryParams
     * @see AbstractTypedQueryParamsBuilder
     */
    int updateUsing(TypedQueryParams<T> queryParams, List<Consumer<CriteriaUpdate<T>>> updates);

    /**
     * Returns the number of rows in the entity managed by this repository
     *
     * @return the number of rows in the entity managed by this repository
     */
    long count();

    /**
     * Returns the the largest auto generated id in the entity managed by this repository; or <tt>ZERO</tt> if no entry exists
     *
     * @return the the largest auto generated id in the entity managed by this repository; or <tt>ZERO</tt> if no entry exists
     */
    long checkpoint();
}
