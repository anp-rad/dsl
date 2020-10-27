package com.aspirecsl.dsl.db;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.persistence.criteria.CriteriaUpdate;
import javax.persistence.metamodel.SingularAttribute;

/**
 * A simple entity updater that uses a <tt>CriteriaUpdate</tt> query to update an entity.
 *
 * @param <E> type of the entity updated by this updater
 * @author anoopr
 */
public class SimpleEntityUpdater<E> {

    /**
     * the JPA repository for the entity object
     **/
    private final BaseRepository<E> repository;

    /**
     * the query params matching the entities to be updated
     **/
    private final TypedQueryParams<E> queryParams;

    /**
     * the updates to be applied
     **/
    private final List<Consumer<CriteriaUpdate<E>>> updates;

    /**
     * Constructs an instance with the supplied values
     *
     * @param repository  the JPA repository for the entity object
     * @param queryParams the query params matching the entities to be updated
     */
    private SimpleEntityUpdater(BaseRepository<E> repository, TypedQueryParams<E> queryParams) {
        this.repository = repository;
        this.queryParams = queryParams;

        updates = new ArrayList<>();
    }

    /**
     * Returns a new <tt>SimpleEntityUpdater</tt> that updates entities matched by the specified <tt>queryParams</tt> using
     * the the given <tt>repository</tt>
     *
     * @param repository  the JPA repository for the entity object
     * @param queryParams the <tt>queryParams</tt> matching the entities to be updated
     * @return a new <tt>SimpleEntityUpdater</tt> that updates entities matched by the specified <tt>queryParams</tt> using
     * the the given <tt>repository</tt>
     */
    public static <E> SimpleEntityUpdater<E> of(BaseRepository<E> repository, TypedQueryParams<E> queryParams) {
        return new SimpleEntityUpdater<>(repository, queryParams);
    }

    /**
     * Sets the specified <tt>attribute</tt> in the entity to the given <tt>value</tt> by using a <tt>CriteriaUpdate</tt> query
     *
     * @param attribute the entity's <em>single-value</em> attribute to update
     * @param value     the new value for the attribute
     * @param <V>       type of the value represented by the attribute
     */
    synchronized <V> void attribute(SingularAttribute<E, V> attribute, V value) {
        updates.add(cu -> cu.set(attribute, value));
    }

    /**
     * Applies updates to the entities matching the <tt>queryParams</tt>
     *
     * @return the number of updated entities
     * @throws RuntimeException if the specified <tt>updates</tt> is <tt>empty</tt>
     */
    final int submit() {
        if (updates.isEmpty()) {
            throw new RuntimeException("no updates have been set");
        }
        return repository.updateUsing(queryParams, updates);
    }
}
