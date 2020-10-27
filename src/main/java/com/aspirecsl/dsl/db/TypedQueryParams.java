package com.aspirecsl.dsl.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

/**
 * Holds the parameters required to build a JPA <em>criteria</em>. The criteria thus built is then used to select, update or
 * delete entities in the database. Users can specify the predicates to filter the results, the path from which to retrieve the
 * results <em>(a.k.a joins with the associated)</em>, and the order in which the results are sorted.
 *
 * <p>The predicates define the <b>WHERE</b> part of the SQL query. They allow a query to filter the results dynamically thereby
 * removing the need to have a corresponding <em>static</em> repository method for every entity retrieval use case.
 * The predicates can be applied to the attributes of the entity being queried or on the attributes of any entities that are
 * <em>joined</em> with the entity being queried. When predicates are to be applied on <em>joined</em> entities the users must
 * also specify the join (or fetch) path to which the predicate applies.
 *
 * <p>The <em>path</em> defines the <b>FROM</b> part of the SQL query. This can be the entity root alone <em>(in most cases)</em>
 * or a <em>joined</em> path if there predicates to be applied on the <em>joined</em> entities. If the user requires the
 * attributes from the <em>joined</em> entity to be fetched along with the root entity then a JPA <tt>fetch</tt> path should
 * be defined. A JPA <tt>fetch</tt> path is a <tt>join</tt> that also retrieves the attributes from the <em>joined</em> entities.
 * It is vital that a <tt>fetch</tt> is defined instead of a <tt>join</tt> if there are <em>lazily</em> fetched associations in
 * the root entity that are required to be present in the <b>SELECT</b> part of the SQL.
 *
 * <p>The <em>order</em> defines the <b>ORDER BY</b> part of the SQL query. Users can specify <tt>ascending</tt> or
 * <tt>descending</tt> order over the results returned by the query. If no explicit ordering is specified by the user, then
 * the order of the entities fetched from the database is undefined.
 *
 * @param <T> the type of the JPA entity object
 * @author aspirecsl
 */
public class TypedQueryParams<T> {

    /**
     * Function that accepts an entity's JPA <tt>Root</tt> path and returns the same JPA <tt>Root</tt>
     * or a JPA <tt>Join</tt> path between the entity and one or more of its joined entities.
     * <p>Implementations of the <tt>TypedQueryParams</tt> may join the entity root with other entities depending on
     * the predicate(s) being applied and return a JPA <tt>Join</tt> path with the relevant associated entity.
     */
    private final Function<From<?, T>, From<?, T>> pathGenerator;

    /**
     * Function that accepts a <tt>CriteriaBuilder</tt> and the entity's JPA <tt>From</tt> path
     * and returns a predicate to filter the entities fetched from the database
     **/
    private final BiFunction<CriteriaBuilder, From<?, T>, Predicate> predicateGenerator;

    /**
     * List of functions that accept a <tt>CriteriaBuilder</tt> and the entity's JPA <tt>From</tt> path
     * and returns the ordering information of the entities fetched from the database
     **/
    private final List<BiFunction<CriteriaBuilder, From<?, T>, Order>> orderByGenerators;

    /**
     * Constructs an instance with the supplied values
     *
     * @param pathGenerator      function that accepts an entity's JPA <tt>Root</tt> path and returns the same JPA <tt>Root</tt>
     *                           or a JPA <tt>Join</tt> path between the entity and one or more of its joined entities
     * @param predicateGenerator function that accepts a <tt>CriteriaBuilder</tt> and the entity's JPA <tt>From</tt> path
     *                           and returns a predicate to filter the entities fetched from the database
     * @param orderByGenerators  list of functions that accept a <tt>CriteriaBuilder</tt> and the entity's JPA <tt>From</tt> path
     *                           and returns the ordering information of the entities fetched from the database
     */
    TypedQueryParams(Function<From<?, T>, From<?, T>> pathGenerator,
            BiFunction<CriteriaBuilder, From<?, T>, Predicate> predicateGenerator,
            List<BiFunction<CriteriaBuilder, From<?, T>, Order>> orderByGenerators) {
        this.pathGenerator = Objects.requireNonNull(pathGenerator);
        this.orderByGenerators = Objects.requireNonNull(orderByGenerators);
        this.predicateGenerator = Objects.requireNonNull(predicateGenerator);
    }

    /**
     * Returns a <tt>TypedQueryParams</tt> object representing the logical AND of the specified <tt>queryParamsCollection</tt>
     *
     * <p>The main purpose of this method is to provide a <em>fluent</em> API to compose entity filter predicates.
     *
     * <p>For example:- to filter payments for a specific account number and status, users can use this method as:
     * <em>where(accountNumberEquals("1234567890"), statusEquals("SUCCESSFUL"))</em> with the accountNumberEquals(...) and the
     * statusEquals(...) methods building the relevant <tt>TypedQueryParams</tt> object for the payment.
     *
     * @param queryParamsCollection the list of query params to filter the entity
     * @param <T>                   the type of the entity the specified <tt>queryParamsCollection</tt> applies to
     * @return a <tt>TypedQueryParams</tt> object representing the logical AND of the specified <tt>queryParamsCollection</tt>
     */
    @SafeVarargs
    public static <T> TypedQueryParams<T> where(TypedQueryParams<T>... queryParamsCollection) {
        return Arrays.stream(Objects.requireNonNull(queryParamsCollection))
                .reduce(TypedQueryParams::and)
                .orElseThrow(() -> new RuntimeException("At least one TypedQueryParams object should be specified"));
    }

    /**
     * Returns a new <tt>TypedQueryParams</tt> object representing the logical OR of this object's <tt>predicateGenerator</tt>
     * and the specified <tt>predicateGenerator</tt>
     *
     * @param predicateGenerator function that accepts a <tt>CriteriaBuilder</tt> and the entity's JPA <tt>From</tt> path
     *                           and returns a predicate to filter the entities fetched from the database
     * @return a new <tt>TypedQueryParams</tt> object representing the logical OR of this object's <tt>predicateGenerator</tt>
     * and the specified <tt>predicateGenerator</tt>
     */
    public TypedQueryParams<T> or(BiFunction<CriteriaBuilder, From<?, T>, Predicate> predicateGenerator) {
        return new TypedQueryParams<>(pathGenerator,
                AbstractTypedQueryParamsBuilder.or(this.predicateGenerator, predicateGenerator),
                orderByGenerators);
    }

    /**
     * Returns a new <tt>TypedQueryParams</tt> object representing the logical AND of this object's <tt>predicateGenerator</tt>
     * and the specified <tt>predicateGenerator</tt>
     *
     * @param predicateGenerator function that accepts a <tt>CriteriaBuilder</tt> and the entity's JPA <tt>From</tt> path
     *                           and returns a predicate to filter the entities fetched from the database
     * @return a new <tt>TypedQueryParams</tt> object representing the logical AND of this object's <tt>predicateGenerator</tt>
     * and the specified <tt>predicateGenerator</tt>
     */
    public TypedQueryParams<T> and(BiFunction<CriteriaBuilder, From<?, T>, Predicate> predicateGenerator) {
        return new TypedQueryParams<>(pathGenerator,
                AbstractTypedQueryParamsBuilder.and(this.predicateGenerator, predicateGenerator),
                orderByGenerators);
    }

    /**
     * Returns a new <tt>TypedQueryParams</tt> object representing the logical OR of this and the <em>other</em>
     * <tt>TypedQueryParams</tt> object
     *
     * @param other the other <tt>TypedQueryParams</tt> object
     * @return a new <tt>TypedQueryParams</tt> object representing the logical OR of this and the <em>other</em>
     * <tt>TypedQueryParams</tt> object
     */
    public TypedQueryParams<T> or(TypedQueryParams<T> other) {
        final List<BiFunction<CriteriaBuilder, From<?, T>, Order>> combinedOrderByGenerators =
                new ArrayList<>(this.orderByGenerators);
        combinedOrderByGenerators.addAll(other.orderByGenerators);

        return new TypedQueryParams<>(this.pathGenerator.andThen(other.pathGenerator),
                AbstractTypedQueryParamsBuilder.or(this.predicateGenerator, other.predicateGenerator),
                combinedOrderByGenerators);
    }

    /**
     * Returns a new <tt>TypedQueryParams</tt> object representing the logical AND of this and the <em>other</em>
     * <tt>TypedQueryParams</tt> object
     *
     * @param other the other <tt>TypedQueryParams</tt> object
     * @return a new <tt>TypedQueryParams</tt> object representing the logical AND of this and the <em>other</em>
     * <tt>TypedQueryParams</tt> object
     */
    public TypedQueryParams<T> and(TypedQueryParams<T> other) {

        final List<BiFunction<CriteriaBuilder, From<?, T>, Order>> combinedOrderByGenerators =
                new ArrayList<>(this.orderByGenerators);
        combinedOrderByGenerators.addAll(other.orderByGenerators);

        return new TypedQueryParams<>(this.pathGenerator.andThen(other.pathGenerator),
                AbstractTypedQueryParamsBuilder.and(this.predicateGenerator, other.predicateGenerator),
                combinedOrderByGenerators);
    }

    /**
     * Returns the entity's <tt>Root</tt> path or a <tt>Join</tt> path between the entity and one or more of its joined entities
     *
     * @param root the JPA <tt>Root</tt> of the entity.
     * @return the entity's <tt>Root</tt> path or a <tt>Join</tt> path between the entity and one or more of its joined entities
     */
    public From<?, T> finalPath(Root<T> root) {
        return pathGenerator.apply(root);
    }

    /**
     * Returns a JPA <tt>Predicate</tt> that filters the entity records fetched from the database
     *
     * @param criteriaBuilder the <tt>CriteriaBuilder</tt> from the persistence context's entity manager
     * @param path            the JPA <tt>Path</tt> to query the entity from
     * @return a JPA <tt>Predicate</tt> that filters the entity records fetched from the database
     */
    public Predicate predicate(CriteriaBuilder criteriaBuilder, From<?, T> path) {
        return predicateGenerator.apply(criteriaBuilder, path);
    }

    /**
     * Returns a list of JPA <tt>Order</tt> information for the entity records fetched from the database.
     * <p>Entities fetched from the database are ordered in either <tt>ascending</tt> or <tt>descending</tt> order. If no explicit
     * ordering is specified by the user, then ordering of the entities fetched from the database is undefined.
     *
     * @param criteriaBuilder the <tt>CriteriaBuilder</tt> from the persistence context's entity manager
     * @param path            the JPA <tt>Path</tt> to query the entity from
     * @return a list of JPA <tt>Order</tt> information for the entity records fetched from the database
     */
    public List<Order> orderBy(CriteriaBuilder criteriaBuilder, From<?, T> path) {
        return orderByGenerators
                .stream()
                .map(e -> e.apply(criteriaBuilder, path))
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    }
}
