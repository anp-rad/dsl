package com.aspirecsl.dsl.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Predicate;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.Bindable;
import javax.persistence.metamodel.ListAttribute;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;

import static java.lang.String.format;
import static java.util.function.Function.identity;
import static javax.persistence.criteria.JoinType.INNER;

/**
 * Builder of a <tt>TypedQueryParams</tt> object.
 *
 * <p>This class is not reusable. It keeps the query metadata <em>(predicates, joins and orderings)</em> to be applied to a query
 * in its instance variables. So re-using an instance of this class with new query metadata will generate an <em>invalid</em>
 * query as the existing query meta-data is applied along with the new ones.
 *
 * <p>This class is not thread-safe. Its behaviour is dependent on <em>non-final</em> instance variables. Users should synchronise
 * access to the methods of this object if it is used in multi-threaded situations.
 *
 * @param <T>    the type of the entity object
 * @param <SELF> the actual object at runtime
 * @author aspirecsl
 * @see TypedQueryParams
 */
public abstract class AbstractTypedQueryParamsBuilder<SELF extends AbstractTypedQueryParamsBuilder<SELF, T>, T> {

    /**
     * provides the ordering applied over the results returned by the query
     **/
    final List<BiFunction<CriteriaBuilder, From<?, T>, Order>> orderByGenerators;
    /**
     * dictates how the next predicate fuses <em>(and/or)</em> with the predicates already set in this object
     **/
    String predicateConnector;
    /**
     * provides the JPA path <em>(FROM in an SQL query) of the query </em>
     **/
    Function<From<?, T>, From<?, T>> pathGenerator;
    /**
     * provides the predicate to apply on the results returned by the query
     **/
    BiFunction<CriteriaBuilder, From<?, T>, Predicate> predicateGenerator;

    /**
     * Constructs an instance by setting the fields to their relevant default values
     */
    private AbstractTypedQueryParamsBuilder() {
        predicateConnector = "and";
        pathGenerator = identity();
        orderByGenerators = new ArrayList<>();
    }

    /**
     * Returns a composed <tt>Predicate</tt> function representing the logical AND of the supplied <tt>predicateGenerators</tt>.
     * <p>If any of the <tt>Predicate</tt> function in the given <tt>predicateGenerators</tt> list is <tt>null</tt>, then it
     * is not included in the resulting composed <tt>Predicate</tt> function.
     *
     * @param predicateGenerators functions that accept a <tt>CriteriaBuilder</tt> and the entity's JPA <tt>From</tt> path
     *                            and return a predicate to filter the entities fetched from the database
     * @return a composed <tt>Predicate</tt> function representing the logical AND of the supplied <tt>predicateGenerators</tt>
     */
    @SafeVarargs
    static <T> BiFunction<CriteriaBuilder, From<?, T>, Predicate>
    and(BiFunction<CriteriaBuilder, From<?, T>, Predicate>... predicateGenerators) {
        return (cb, root) ->
                cb.and(Arrays.stream(predicateGenerators)
                        .filter(Objects::nonNull)
                        .map(e -> e.apply(cb, root))
                        .toArray(Predicate[]::new));
    }

    /**
     * Returns a composed <tt>Predicate</tt> function representing the logical OR of the supplied <tt>predicateGenerators</tt>.
     * <p>If any of the <tt>Predicate</tt> function in the given <tt>predicateGenerators</tt> list is <tt>null</tt>, then it
     * is not included in the resulting composed <tt>Predicate</tt> function.
     *
     * @param predicateGenerators functions that accept a <tt>CriteriaBuilder</tt> and the entity's JPA <tt>From</tt> path
     *                            and return a predicate to filter the entities fetched from the database
     * @return a composed <tt>Predicate</tt> function representing the logical OR of the supplied <tt>predicateGenerators</tt>
     */
    @SafeVarargs
    static <T> BiFunction<CriteriaBuilder, From<?, T>, Predicate>
    or(BiFunction<CriteriaBuilder, From<?, T>, Predicate>... predicateGenerators) {
        return (cb, root) ->
                cb.or(Arrays.stream(predicateGenerators)
                        .filter(Objects::nonNull)
                        .map(e -> e.apply(cb, root))
                        .toArray(Predicate[]::new));
    }

    /**
     * Returns the JPA <tt>join</tt> or <tt>fetch-join</tt> path to query the entity and the relevant <em>joined</em> entity
     *
     * @param joinedAttribute the attribute representing the <tt>joined</tt> entity
     * @param root            the entity's root path
     * @param <T>             the type of the entity
     * @param <J>             the type of the <em>joined</em> entity
     * @param <JOIN>          the type of the attribute representing the <tt>joined</tt> entity
     * @return the JPA <tt>join</tt> or <tt>fetch-join</tt> path to query the entity and the relevant <em>joined</em> entity
     * @throws RuntimeException if no <tt>join</tt> or <tt>fetch-join</tt> has been made between the entities
     */
    private static <T, J, JOIN extends Bindable<J> & Attribute<T, ?>> Join<T, J> getJoinPath(JOIN joinedAttribute,
            From<?, T> root) {
        return Optional.ofNullable(getNullableJoinOrFetch(joinedAttribute, root))
                .orElseThrow(() ->
                        new RuntimeException(format("No JOIN or FETCH made between [%s] and [%s]",
                                root.getJavaType().getSimpleName(),
                                joinedAttribute.getJavaType().getSimpleName())));
    }

    /**
     * Returns the JPA <tt>join</tt> or <tt>fetch-join</tt> path to query the entity and the relevant <em>joined</em> entity if a
     * <tt>join</tt> or <tt>fetch-join</tt> exists; Otherwise <tt>null</tt>
     *
     * @param joinedAttribute the attribute representing the <tt>joined</tt> entity
     * @param root            the entity's root path
     * @param <T>             the type of the entity
     * @param <J>             the type of the <em>joined</em> entity
     * @param <JOIN>          the type of the attribute representing the <tt>joined</tt> entity
     * @return the JPA <tt>join</tt> or <tt>fetch-join</tt> path to query the entity and the relevant <em>joined</em> entity if a
     * <tt>join</tt> or <tt>fetch-join</tt> exists; Otherwise <tt>null</tt>
     */
    @SuppressWarnings("unchecked")
    private static <T, J, JOIN extends Bindable<J> & Attribute<T, ?>> Join<T, J> getNullableJoinOrFetch(JOIN joinedAttribute,
            From<?, T> root) {
        return root.getJoins()
                .stream()
                .filter(e -> e.getAttribute().equals(joinedAttribute))
                .findAny()
                .map(join -> (Join<T, J>) join)
                // if no joins are present, then check if any (join)fetches exist
                .orElseGet(() ->
                        (Join<T, J>) root.getFetches()
                                .stream()
                                .filter(e -> e.getAttribute().equals(joinedAttribute))
                                .findAny()
                                .orElse(null));
    }

    /**
     * Returns a <tt>PredicateApplier</tt> to add the predicates to be applied on the results returned by the query
     *
     * @return a <tt>PredicateApplier</tt> to add the predicates to be applied on the results returned by the query
     * @throws RuntimeException if predicates already exist for this query params builder
     */
    public PredicateApplier<T, SELF> predicate() {
        if (predicateGenerator != null) {
            throw new RuntimeException(
                    "Predicates exist for this query params builder. Create a new builder if this is a new query");
        }
        return new PredicateApplier<>(self());
    }

    /**
     * Specifies an <tt>INNER join</tt> to be made between the entity retrieved and the entity represented by the given <tt>attribute</tt>
     *
     * @param attribute the single-valued property representing the <em>joined</em> entity
     * @return this builder after specifying the relevant <tt>join</tt>
     */
    public SELF join(SingularAttribute<T, ?> attribute) {
        return join(attribute, INNER, false);
    }

    /**
     * Specifies an <tt>INNER fetch-join</tt> to be made between the entity retrieved and the entity represented by the given
     * <tt>attribute</tt>. A <tt>fetch-join</tt> differs from a simple <tt>join</tt> in that the attributes of the joined entities
     * are fetched along with the root entity <em>(in the SELECT part of the query)</em> in the former.
     *
     * @param attribute the single-valued property representing the <em>join-fetched</em> entity
     * @return this builder after specifying the relevant <tt>fetch-join</tt>
     */
    public SELF fetch(SingularAttribute<T, ?> attribute) {
        return join(attribute, INNER, true);
    }

    /**
     * Specifies a <tt>join</tt> or <tt>fetch-join</tt> to be made between the entity retrieved and the entity represented by the
     * given <tt>attribute</tt>. A <tt>fetch-join</tt> differs from a simple <tt>join</tt> in that the attributes of the joined
     * entities are fetched along with the root entity <em>(in the SELECT part of the query)</em> in the former.
     *
     * @param attribute the single-valued property representing the <em>joined</em> or <em>join-fetched</em> entity
     * @param joinType  the type of this join - <tt>INNER</tt>, <tt>LEFT</tt> or <tt>RIGHT</tt>. Users should use the <tt>RIGHT</tt>
     *                  join type with caution as they are not required to be supported in JPA 2.0 and hence may not be portable
     *                  between JPA providers.
     * @param fetch     <tt>True</tt> if a <em>fetch</em> join is to be made; <tt>False</tt> otherwise
     * @return this builder after specifying the relevant <tt>join</tt> or <tt>fetch-join</tt>
     */
    public SELF join(SingularAttribute<T, ?> attribute, JoinType joinType, boolean fetch) {
        pathGenerator = pathGenerator.andThen(root -> {
            // add a join (or fetch) on this attribute if one doesn't exist already
            Join<T, ?> joinOrFetch = getNullableJoinOrFetch(attribute, root);
            if (joinOrFetch == null || (joinType != INNER && joinOrFetch.getJoinType() != joinType)) {
                // root.fetch() returns a Fetch object that can be safely cast to a Join for both Hibernate and EclipseLink
                // noinspection unchecked
                joinOrFetch = fetch ? (Join<T, ?>) root.fetch(attribute, joinType) : root.join(attribute, joinType);
            }
            return joinOrFetch.getParent();
        });
        return self();
    }

    /**
     * Specifies an <tt>INNER join</tt> to be made between the entity retrieved and the entity represented by the given <tt>attribute</tt>
     *
     * @param attribute the set-valued property representing the <em>joined</em> entity
     * @return this builder after specifying the relevant <tt>join</tt>
     */
    public SELF join(SetAttribute<T, ?> attribute) {
        return join(attribute, INNER, false);
    }

    /**
     * Specifies an <tt>INNER fetch-join</tt> to be made between the entity retrieved and the entity represented by the given
     * <tt>attribute</tt>. A <tt>fetch-join</tt> differs from a simple <tt>join</tt> in that the attributes of the joined entities
     * are fetched along with the root entity <em>(in the SELECT part of the query)</em> in the former.
     *
     * @param attribute the set-valued property representing the <em>join-fetched</em> entity
     * @return this builder after specifying the relevant <tt>fetch-join</tt>
     */
    public SELF fetch(SetAttribute<T, ?> attribute) {
        return join(attribute, INNER, true);
    }

    /**
     * Specifies a <tt>join</tt> or <tt>fetch-join</tt> to be made between the entity retrieved and the entity represented by the
     * given <tt>attribute</tt>. A <tt>fetch-join</tt> differs from a simple <tt>join</tt> in that the attributes of the joined
     * entities are fetched along with the root entity <em>(in the SELECT part of the query)</em> in the former.
     *
     * @param attribute the set-valued property representing the <em>joined</em> or <em>join-fetched</em> entity
     * @param joinType  the type of this join - <tt>INNER</tt>, <tt>LEFT</tt> or <tt>RIGHT</tt>. Users should use the <tt>RIGHT</tt>
     *                  join type with caution as they are not required to be supported in JPA 2.0 and hence may not be portable
     *                  between JPA providers.
     * @param fetch     <tt>True</tt> if a <em>fetch</em> join is to be made; <tt>False</tt> otherwise
     * @return this builder after specifying the relevant <tt>join</tt> or <tt>fetch-join</tt>
     */
    public SELF join(SetAttribute<T, ?> attribute, JoinType joinType, boolean fetch) {
        pathGenerator = pathGenerator.andThen(root -> {
            // add a join (or fetch) on this attribute if one doesn't exist already
            Join<T, ?> joinOrFetch = getNullableJoinOrFetch(attribute, root);
            if (joinOrFetch == null || (joinType != INNER && joinOrFetch.getJoinType() != joinType)) {
                // root.fetch() returns a Fetch object that can be safely cast to a Join for both Hibernate and EclipseLink
                // noinspection unchecked
                joinOrFetch = fetch ? (Join<T, ?>) root.fetch(attribute, joinType) : root.join(attribute, joinType);
            }
            return joinOrFetch.getParent();
        });
        return self();
    }

    /**
     * Specifies an <tt>INNER join</tt> to be made between the entity retrieved and the entity represented by the given <tt>attribute</tt>
     *
     * @param attribute the list-valued property representing the <em>joined</em> entity
     * @return this builder after specifying the relevant <tt>join</tt>
     */
    public SELF join(ListAttribute<T, ?> attribute) {
        return join(attribute, INNER, false);
    }

    /**
     * Specifies an <tt>INNER fetch-join</tt> to be made between the entity retrieved and the entity represented by the given
     * <tt>attribute</tt>. A <tt>fetch-join</tt> differs from a simple <tt>join</tt> in that the attributes of the joined entities
     * are fetched along with the root entity <em>(in the SELECT part of the query)</em> in the former.
     *
     * @param attribute the list-valued property representing the <em>join-fetched</em> entity
     * @return this builder after specifying the relevant <tt>fetch-join</tt>
     */
    public SELF fetch(ListAttribute<T, ?> attribute) {
        return join(attribute, INNER, true);
    }

    /**
     * Specifies a <tt>join</tt> or <tt>fetch-join</tt> to be made between the entity retrieved and the entity represented by the
     * given <tt>attribute</tt>. A <tt>fetch-join</tt> differs from a simple <tt>join</tt> in that the attributes of the joined
     * entities are fetched along with the root entity <em>(in the SELECT part of the query)</em> in the former.
     *
     * @param attribute the list-valued property representing the <em>joined</em> or <em>join-fetched</em> entity
     * @param joinType  the type of this join - <tt>INNER</tt>, <tt>LEFT</tt> or <tt>RIGHT</tt>. Users should use the <tt>RIGHT</tt>
     *                  join type with caution as they are not required to be supported in JPA 2.0 and hence may not be portable
     *                  between JPA providers.
     * @param fetch     <tt>True</tt> if a <em>fetch</em> join is to be made; <tt>False</tt> otherwise
     * @return this builder after specifying the relevant <tt>join</tt> or <tt>fetch-join</tt>
     */
    public SELF join(ListAttribute<T, ?> attribute, JoinType joinType, boolean fetch) {
        pathGenerator = pathGenerator.andThen(root -> {
            // add a join (or fetch) on this attribute if one doesn't exist already
            Join<T, ?> joinOrFetch = getNullableJoinOrFetch(attribute, root);
            if (joinOrFetch == null || (joinType != INNER && joinOrFetch.getJoinType() != joinType)) {
                // root.fetch() returns a Fetch object that can be safely cast to a Join for both Hibernate and EclipseLink
                // noinspection unchecked
                joinOrFetch = fetch ? (Join<T, ?>) root.fetch(attribute, joinType) : root.join(attribute, joinType);
            }
            return joinOrFetch.getParent();
        });
        return self();
    }

    /**
     * Specifies the results of the query to be ordered in <tt>ascending</tt> order of the given <tt>attribute</tt>.
     *
     * @param attribute the single-valued property representing an attribute in the entity
     * @return this builder after specifying the ascending ordering of the results
     */
    public SELF ascending(SingularAttribute<T, ?> attribute) {
        orderByGenerators.add((cb, root) -> cb.asc(root.get(attribute)));
        return self();
    }

    /**
     * Specifies the results of the query to be ordered in <tt>ascending</tt> order of the given <tt>attribute</tt> from a
     * <em>joined</em> entity.
     *
     * @param joinedAttribute the attribute representing the <tt>joined</tt> entity
     * @param attribute       the attribute in the <em>joined</em> entity
     * @param <J>             the type of the <em>joined</em> entity
     * @param <JOIN>          the type of the attribute representing the <tt>joined</tt> entity
     * @return this builder after specifying the ascending ordering of the results
     */
    public <J, JOIN extends Bindable<J> & Attribute<T, ?>>
    SELF ascending(JOIN joinedAttribute, SingularAttribute<J, ?> attribute) {
        orderByGenerators.add((cb, root) -> {
            final Join<T, J> joinPath = getJoinPath(joinedAttribute, root);
            return cb.asc(joinPath.get(attribute));
        });
        return self();
    }

    /**
     * Specifies the results of the query to be ordered in <tt>descending</tt> order of the given <tt>attribute</tt>.
     *
     * @param attribute the single-valued property representing an attribute in the entity
     * @return this builder after specifying the descending ordering of the results
     */
    public SELF descending(SingularAttribute<T, ?> attribute) {
        orderByGenerators.add((cb, root) -> cb.desc(root.get(attribute)));
        return self();
    }

    /**
     * Specifies the results of the query to be ordered in <tt>descending</tt> order of the given <tt>attribute</tt> from a
     * <em>joined</em> entity.
     *
     * @param joinedAttribute the attribute representing the <tt>joined</tt> entity
     * @param attribute       the attribute in the <em>joined</em> entity
     * @param <J>             the type of the <em>joined</em> entity
     * @param <JOIN>          the type of the attribute representing the <tt>joined</tt> entity
     * @return this builder after specifying the descending ordering of the results
     */
    public <J, JOIN extends Bindable<J> & Attribute<T, ?>>
    SELF descending(JOIN joinedAttribute, SingularAttribute<J, ?> attribute) {
        orderByGenerators.add((cb, root) -> {
            final Join<T, J> joinPath = getJoinPath(joinedAttribute, root);
            return cb.desc(joinPath.get(attribute));
        });
        return self();
    }

    /**
     * Returns the object casting it to the corresponding class in its inheritance hierarchy
     * <p>
     * The cast is safe and will not throw {@code ClassCastException} as the object is only
     * cast to its corresponding class in its inheritance hierarchy
     *
     * @return an object cast to its corresponding class in its inheritance hierarchy
     */
    @SuppressWarnings("unchecked")
    private SELF self() {
        return (SELF) this;
    }

    /**
     * Builds a <tt>Predicate</tt> generator that will be used to filter the results returned by the query
     *
     * @param <T>       the type of the entity object
     * @param <BUILDER> type of the <tt>AbstractTypedQueryParamsBuilder</tt> object
     */
    public static class PredicateApplier<T, BUILDER extends AbstractTypedQueryParamsBuilder<BUILDER, T>> {

        /**
         * the <tt>AbstractTypedQueryParamsBuilder</tt> object that creates this <tt>PredicateApplier</tt> instance
         **/
        private final BUILDER builder;

        /**
         * Constructs a new instance with the supplied <tt>AbstractTypedQueryParamsBuilder</tt> object
         *
         * @param builder the <tt>AbstractTypedQueryParamsBuilder</tt> object that creates this <tt>PredicateApplier</tt> instance
         */
        private PredicateApplier(BUILDER builder) {
            this.builder = builder;
        }

        /**
         * Returns a <tt>PredicateGenerator</tt> for the given <tt>attribute</tt>
         *
         * @param attribute the single-valued property representing a column in the entity
         * @param <V>       type of th value held by the given <tt>attribute</tt>
         * @return a <tt>PredicateGenerator</tt> for the given <tt>attribute</tt>
         */
        public <V> PredicateGenerator<T, ?, V, ?, BUILDER> attribute(SingularAttribute<T, V> attribute) {
            return new PredicateGenerator<>(this, attribute);
        }

        /**
         * Returns a <tt>PredicateGenerator</tt> for the given <tt>attribute</tt> from a <em>joined</em> entity
         *
         * @param joinedAttribute the attribute representing the <tt>joined</tt> entity
         * @param attribute       the single-valued property representing a column in the <em>joined</em> entity
         * @param <J>             the type of the <em>joined</em> entity
         * @param <V>             the type of the value of the given <tt>attribute</tt>
         * @param <JOIN>          the type of the attribute representing the <tt>joined</tt> entity
         * @return a <tt>PredicateGenerator</tt> for the given <tt>attribute</tt> from a <em>joined</em> entity
         */
        public <J, V, JOIN extends Bindable<J> & Attribute<T, ?>>
        PredicateGenerator<T, J, V, JOIN, BUILDER> attribute(JOIN joinedAttribute, SingularAttribute<J, V> attribute) {
            return new PredicateGenerator<>(this, joinedAttribute, attribute);
        }

        /**
         * Returns a <tt>PredicateGenerator</tt> for the given <tt>Comparable attribute</tt>
         *
         * @param attribute the single-valued property representing a column in the entity
         * @param <V>       type of th value held by the given <tt>attribute</tt>
         * @return a <tt>PredicateGenerator</tt> for the given <tt>Comparable attribute</tt>
         */
        public <V extends Comparable<? super V>>
        ComparablePredicateGenerator<T, ?, V, ?, BUILDER> comparableAttribute(SingularAttribute<T, V> attribute) {
            return new ComparablePredicateGenerator<>(this, attribute);
        }

        /**
         * Returns a <tt>PredicateGenerator</tt> for the given <tt>Comparable attribute</tt> from a <em>joined</em> entity
         *
         * @param joinedAttribute the attribute representing the <tt>joined</tt> entity
         * @param attribute       the single-valued property representing a column in the <em>joined</em> entity
         * @param <J>             the type of the <em>joined</em> entity
         * @param <V>             the type of the value of the given <tt>attribute</tt>
         * @param <JOIN>          the type of the attribute representing the <tt>joined</tt> entity
         * @return a <tt>PredicateGenerator</tt> for the given <tt>Comparable attribute</tt> from a <em>joined</em> entity
         */
        public <J, V extends Comparable<? super V>, JOIN extends Bindable<J> & Attribute<T, ?>>
        ComparablePredicateGenerator<T, J, V, JOIN, BUILDER> comparableAttribute(JOIN joinedAttribute,
                SingularAttribute<J, V> attribute) {
            return new ComparablePredicateGenerator<>(this, joinedAttribute, attribute);
        }

        /**
         * Returns a <tt>PredicateGenerator</tt> for the given <tt>numeric attribute</tt>
         *
         * @param attribute the single-valued property representing a column in the entity
         * @param <V>       type of th value held by the given <tt>attribute</tt>
         * @return a <tt>PredicateGenerator</tt> for the given <tt>numeric attribute</tt>
         */
        public <V extends Number & Comparable<? super V>>
        NumericPredicateGenerator<T, ?, V, ?, BUILDER> numericAttribute(SingularAttribute<T, V> attribute) {
            return new NumericPredicateGenerator<>(this, attribute);
        }

        /**
         * Returns a <tt>PredicateGenerator</tt> for the given <tt>numeric attribute</tt> from a <em>joined</em> entity
         *
         * @param joinedAttribute the attribute representing the <tt>joined</tt> entity
         * @param attribute       the single-valued property representing a column in the <em>joined</em> entity
         * @param <J>             the type of the <em>joined</em> entity
         * @param <V>             the type of the value of the given <tt>attribute</tt>
         * @param <JOIN>          the type of the attribute representing the <tt>joined</tt> entity
         * @return a <tt>PredicateGenerator</tt> for the given <tt>numeric attribute</tt> from a <em>joined</em> entity
         */
        public <J, V extends Number & Comparable<? super V>, JOIN extends Bindable<J> & Attribute<T, ?>>
        NumericPredicateGenerator<T, J, V, JOIN, BUILDER> numericAttribute(JOIN joinedAttribute,
                SingularAttribute<J, V> attribute) {
            return new NumericPredicateGenerator<>(this, joinedAttribute, attribute);
        }

        /**
         * Returns a <tt>PredicateGenerator</tt> for the given <tt>String attribute</tt>
         *
         * @param attribute the single-valued property representing a column in the entity
         * @return a <tt>PredicateGenerator</tt> for the given <tt>String attribute</tt>
         */
        public BooleanPredicateGenerator<T, ?, ?, BUILDER> booleanAttribute(SingularAttribute<T, Boolean> attribute) {
            return new BooleanPredicateGenerator<>(this, attribute);
        }

        /**
         * Returns a <tt>PredicateGenerator</tt> for the given <tt>String attribute</tt> from a <em>joined</em> entity
         *
         * @param joinedAttribute the attribute representing the <tt>joined</tt> entity
         * @param attribute       the single-valued property representing a column in the <em>joined</em> entity
         * @param <J>             the type of the <em>joined</em> entity
         * @param <JOIN>          the type of the attribute representing the <tt>joined</tt> entity
         * @return a <tt>PredicateGenerator</tt> for the given <tt>String attribute</tt> from a <em>joined</em> entity
         */
        public <J, JOIN extends Bindable<J> & Attribute<T, ?>>
        BooleanPredicateGenerator<T, J, JOIN, BUILDER> booleanAttribute(JOIN joinedAttribute,
                SingularAttribute<J, Boolean> attribute) {
            return new BooleanPredicateGenerator<>(this, joinedAttribute, attribute);
        }

        /**
         * Returns a <tt>PredicateGenerator</tt> for the given <tt>String attribute</tt>
         *
         * @param attribute the single-valued property representing a column in the entity
         * @return a <tt>PredicateGenerator</tt> for the given <tt>String attribute</tt>
         */
        public StringPredicateGenerator<T, ?, ?, BUILDER> stringAttribute(SingularAttribute<T, String> attribute) {
            return new StringPredicateGenerator<>(this, attribute);
        }

        /**
         * Returns a <tt>PredicateGenerator</tt> for the given <tt>String attribute</tt> from a <em>joined</em> entity
         *
         * @param joinedAttribute the attribute representing the <tt>joined</tt> entity
         * @param attribute       the single-valued property representing a column in the <em>joined</em> entity
         * @param <J>             the type of the <em>joined</em> entity
         * @param <JOIN>          the type of the attribute representing the <tt>joined</tt> entity
         * @return a <tt>PredicateGenerator</tt> for the given <tt>String attribute</tt> from a <em>joined</em> entity
         */
        public <J, JOIN extends Bindable<J> & Attribute<T, ?>>
        StringPredicateGenerator<T, J, JOIN, BUILDER> stringAttribute(JOIN joinedAttribute,
                SingularAttribute<J, String> attribute) {
            return new StringPredicateGenerator<>(this, joinedAttribute, attribute);
        }

        /**
         * Returns the <tt>AbstractTypedQueryParamsBuilder</tt> object tht invoked this <tt>PredicateApplier</tt> instance
         *
         * @return the <tt>AbstractTypedQueryParamsBuilder</tt> object tht invoked this <tt>PredicateApplier</tt> instance
         */
        public BUILDER apply() {
            return builder;
        }
    }

    /**
     * Specifies a JPA <tt>Predicate</tt> on an <tt>attribute</tt> in the entity (or a <em>joined</em> entity) being queried
     *
     * @param <T>       the type of the entity object
     * @param <J>       the type of the <em>joined</em> entity
     * @param <V>       the type of the value in the <tt>attribute</tt> on which the <tt>Predicate</tt> is applied
     * @param <JOIN>    the type of the attribute representing the <tt>joined</tt> entity
     * @param <BUILDER> type of the <tt>AbstractTypedQueryParamsBuilder</tt> object that is building the <tt>TypedQueryParams</tt> object
     */
    public static class PredicateGenerator<T, J, V, JOIN extends Bindable<J> & Attribute<T, ?>, BUILDER extends AbstractTypedQueryParamsBuilder<BUILDER, T>> {

        final JOIN joinedAttribute;
        final SingularAttribute<T, V> attribute;
        final PredicateApplier<T, BUILDER> predicateApplier;
        final SingularAttribute<J, V> attributeOnJoinedEntity;

        /**
         * Constructs an instance with the supplied values
         *
         * @param predicateApplier the <tt>PredicateApplier</tt> object that is adding the predicates
         * @param attribute        the single-valued property representing a column in the entity
         */
        private PredicateGenerator(PredicateApplier<T, BUILDER> predicateApplier, SingularAttribute<T, V> attribute) {
            this(predicateApplier, attribute, null, null);
        }

        /**
         * Constructs an instance with the supplied values
         *
         * @param predicateApplier the <tt>PredicateApplier</tt> object that is adding the predicates
         * @param joinedAttribute  the attribute representing the <tt>joined</tt> entity
         * @param attribute        the single-valued property representing a column in the <em>joined</em> entity
         */
        private PredicateGenerator(PredicateApplier<T, BUILDER> predicateApplier, JOIN joinedAttribute,
                SingularAttribute<J, V> attribute) {
            this(predicateApplier, null, joinedAttribute, attribute);
        }

        /**
         * Constructs an instance with the supplied values
         *
         * @param predicateApplier        the <tt>PredicateApplier</tt> object that is adding the predicates
         * @param attribute               the single-valued property representing a column in the entity
         * @param joinedAttribute         the attribute representing the <tt>joined</tt> entity
         * @param attributeOnJoinedEntity the single-valued property representing a column in the <em>joined</em> entity
         */
        private PredicateGenerator(PredicateApplier<T, BUILDER> predicateApplier, SingularAttribute<T, V> attribute,
                JOIN joinedAttribute, SingularAttribute<J, V> attributeOnJoinedEntity) {
            this.predicateApplier = predicateApplier;
            this.attribute = attribute;
            this.joinedAttribute = joinedAttribute;
            this.attributeOnJoinedEntity = attributeOnJoinedEntity;
        }

        /**
         * Specifies a <tt>Predicate</tt> that evaluates to <tt>True</tt> if the value of the <tt>attribute</tt> in this
         * <tt>PredicateGenerator</tt> object is equal to the given <tt>value</tt>.
         *
         * @param value the value to compare with the <tt>attribute</tt> in this <tt>PredicateGenerator</tt> object
         * @return an <tt>AndOrConnector</tt> object to join this <tt>Predicate</tt> with any other <tt>Predicate</tt>
         */
        public final AndOrConnector<T, BUILDER> isEqualTo(V value) {
            if (joinedAttribute == null) {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator =
                            and(predicateApplier.builder.predicateGenerator, (cb, root) -> cb.equal(root.get(attribute), value));
                } else {
                    predicateApplier.builder.predicateGenerator =
                            or(predicateApplier.builder.predicateGenerator, (cb, root) -> cb.equal(root.get(attribute), value));
                }
            } else {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator = and(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.equal(join.get(attributeOnJoinedEntity), value);
                    });
                } else {
                    predicateApplier.builder.predicateGenerator = or(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.equal(join.get(attributeOnJoinedEntity), value);
                    });
                }
            }
            return new AndOrConnector<>(predicateApplier);
        }

        /**
         * Specifies a <tt>Predicate</tt> that evaluates to <tt>True</tt> if the value of the <tt>attribute</tt> in this
         * <tt>PredicateGenerator</tt> object is one of the given <tt>values</tt>.
         *
         * @param values the values to compare with the <tt>attribute</tt> in this <tt>PredicateGenerator</tt> object
         * @return an <tt>AndOrConnector</tt> object to join this <tt>Predicate</tt> with any other <tt>Predicate</tt>
         */
        public final AndOrConnector<T, BUILDER> isIn(Collection<V> values) {
            if (joinedAttribute == null) {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator = and(
                            predicateApplier.builder.predicateGenerator, (cb, root) -> root.get(attribute).in(values));
                } else {
                    predicateApplier.builder.predicateGenerator = or(
                            predicateApplier.builder.predicateGenerator, (cb, root) -> root.get(attribute).in(values));
                }
            } else {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator = and(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return join.get(attributeOnJoinedEntity).in(values);
                    });
                } else {
                    predicateApplier.builder.predicateGenerator = or(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return join.get(attributeOnJoinedEntity).in(values);
                    });
                }
            }
            return new AndOrConnector<>(predicateApplier);
        }

        /**
         * Specifies a <tt>Predicate</tt> that evaluates to <tt>True</tt> if the value of the <tt>attribute</tt> in this
         * <tt>PredicateGenerator</tt> object is <tt>null</tt>.
         *
         * @return an <tt>AndOrConnector</tt> object to join this <tt>Predicate</tt> with any other <tt>Predicate</tt>
         */
        public final AndOrConnector<T, BUILDER> isNull() {
            if (joinedAttribute == null) {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator =
                            and(predicateApplier.builder.predicateGenerator, (cb, root) -> cb.isNull(root.get(attribute)));
                } else {
                    predicateApplier.builder.predicateGenerator =
                            or(predicateApplier.builder.predicateGenerator, (cb, root) -> cb.isNull(root.get(attribute)));
                }
            } else {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator = and(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.isNull(join.get(attributeOnJoinedEntity));
                    });
                } else {
                    predicateApplier.builder.predicateGenerator = or(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.isNull(join.get(attributeOnJoinedEntity));
                    });
                }
            }
            return new AndOrConnector<>(predicateApplier);
        }

        /**
         * Specifies a <tt>Predicate</tt> that evaluates to <tt>True</tt> if the value of the <tt>attribute</tt> in this
         * <tt>PredicateGenerator</tt> object is <tt>not null</tt>.
         *
         * @return an <tt>AndOrConnector</tt> object to join this <tt>Predicate</tt> with any other <tt>Predicate</tt>
         */
        public final AndOrConnector<T, BUILDER> isNotNull() {
            if (joinedAttribute == null) {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator =
                            and(predicateApplier.builder.predicateGenerator, (cb, root) -> cb.isNotNull(root.get(attribute)));
                } else {
                    predicateApplier.builder.predicateGenerator =
                            or(predicateApplier.builder.predicateGenerator, (cb, root) -> cb.isNotNull(root.get(attribute)));
                }
            } else {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator = and(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.isNotNull(join.get(attributeOnJoinedEntity));
                    });
                } else {
                    predicateApplier.builder.predicateGenerator = or(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.isNotNull(join.get(attributeOnJoinedEntity));
                    });
                }
            }
            return new AndOrConnector<>(predicateApplier);
        }
    }

    /**
     * Specifies a JPA <tt>Predicate</tt> on an <tt>Comparable attribute</tt> in the entity (or a <em>joined</em> entity) being queried
     *
     * @param <T>       the type of the entity object
     * @param <J>       the type of the <em>joined</em> entity
     * @param <V>       the type of the value in the <tt>Comparable attribute</tt> on which the <tt>Predicate</tt> is applied
     * @param <JOIN>    the type of the attribute representing the <tt>joined</tt> entity
     * @param <BUILDER> type of the <tt>AbstractTypedQueryParamsBuilder</tt> object that is building the <tt>TypedQueryParams</tt> object
     */
    public static class ComparablePredicateGenerator<T, J, V extends Comparable<? super V>, JOIN extends Bindable<J> & Attribute<T, ?>, BUILDER extends AbstractTypedQueryParamsBuilder<BUILDER, T>>
            extends PredicateGenerator<T, J, V, JOIN, BUILDER> {

        /**
         * Constructs an instance with the supplied values
         *
         * @param predicateApplier the <tt>PredicateApplier</tt> object that is adding the predicates
         * @param attribute        the single-valued property representing a column in the entity
         */
        private ComparablePredicateGenerator(PredicateApplier<T, BUILDER> predicateApplier, SingularAttribute<T, V> attribute) {
            this(predicateApplier, attribute, null, null);
        }

        /**
         * Constructs an instance with the supplied values
         *
         * @param predicateApplier the <tt>PredicateApplier</tt> object that is adding the predicates
         * @param joinedAttribute  the attribute representing the <tt>joined</tt> entity
         * @param attribute        the single-valued property representing a column in the <em>joined</em> entity
         */
        private ComparablePredicateGenerator(PredicateApplier<T, BUILDER> predicateApplier, JOIN joinedAttribute,
                SingularAttribute<J, V> attribute) {
            this(predicateApplier, null, joinedAttribute, attribute);
        }

        /**
         * Constructs an instance with the supplied values
         *
         * @param predicateApplier        the <tt>PredicateApplier</tt> object that is adding the predicates
         * @param attribute               the single-valued property representing a column in the entity
         * @param joinedAttribute         the attribute representing the <tt>joined</tt> entity
         * @param attributeOnJoinedEntity the single-valued property representing a column in the <em>joined</em> entity
         */
        private ComparablePredicateGenerator(PredicateApplier<T, BUILDER> predicateApplier, SingularAttribute<T, V> attribute,
                JOIN joinedAttribute, SingularAttribute<J, V> attributeOnJoinedEntity) {
            super(predicateApplier, attribute, joinedAttribute, attributeOnJoinedEntity);
        }

        /**
         * Specifies a <tt>Predicate</tt> that evaluates to <tt>True</tt> if the value of the <tt>attribute</tt> in this
         * <tt>PredicateGenerator</tt> object is less than the given <tt>value</tt>.
         *
         * @param value the value to compare with the <tt>attribute</tt> in this <tt>PredicateGenerator</tt> object
         * @return an <tt>AndOrConnector</tt> object to join this <tt>Predicate</tt> with any other <tt>Predicate</tt>
         */
        public final AndOrConnector<T, BUILDER> isLessThan(V value) {
            if (joinedAttribute == null) {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator =
                            and(predicateApplier.builder.predicateGenerator,
                                    (cb, root) -> cb.lessThan(root.get(attribute), value));
                } else {
                    predicateApplier.builder.predicateGenerator =
                            or(predicateApplier.builder.predicateGenerator,
                                    (cb, root) -> cb.lessThan(root.get(attribute), value));
                }
            } else {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator = and(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.lessThan(join.get(attributeOnJoinedEntity), value);
                    });
                } else {
                    predicateApplier.builder.predicateGenerator = or(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.lessThan(join.get(attributeOnJoinedEntity), value);
                    });
                }
            }
            return new AndOrConnector<>(predicateApplier);
        }

        /**
         * Specifies a <tt>Predicate</tt> that evaluates to <tt>True</tt> if the value of the <tt>attribute</tt> in this
         * <tt>PredicateGenerator</tt> object is less than or equal to the given <tt>value</tt>.
         *
         * @param value the value to compare with the <tt>attribute</tt> in this <tt>PredicateGenerator</tt> object
         * @return an <tt>AndOrConnector</tt> object to join this <tt>Predicate</tt> with any other <tt>Predicate</tt>
         */
        public final AndOrConnector<T, BUILDER> isLessThanOrEqualTo(V value) {
            if (joinedAttribute == null) {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator =
                            and(predicateApplier.builder.predicateGenerator,
                                    (cb, root) -> cb.lessThanOrEqualTo(root.get(attribute), value));
                } else {
                    predicateApplier.builder.predicateGenerator =
                            or(predicateApplier.builder.predicateGenerator,
                                    (cb, root) -> cb.lessThanOrEqualTo(root.get(attribute), value));
                }
            } else {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator = and(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.lessThanOrEqualTo(join.get(attributeOnJoinedEntity), value);
                    });
                } else {
                    predicateApplier.builder.predicateGenerator = or(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.lessThanOrEqualTo(join.get(attributeOnJoinedEntity), value);
                    });
                }
            }
            return new AndOrConnector<>(predicateApplier);
        }

        /**
         * Specifies a <tt>Predicate</tt> that evaluates to <tt>True</tt> if the value of the <tt>attribute</tt> in this
         * <tt>PredicateGenerator</tt> object is greater than the given <tt>value</tt>.
         *
         * @param value the value to compare with the <tt>attribute</tt> in this <tt>PredicateGenerator</tt> object
         * @return an <tt>AndOrConnector</tt> object to join this <tt>Predicate</tt> with any other <tt>Predicate</tt>
         */
        public final AndOrConnector<T, BUILDER> isGreaterThan(V value) {
            if (joinedAttribute == null) {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator =
                            and(predicateApplier.builder.predicateGenerator,
                                    (cb, root) -> cb.greaterThan(root.get(attribute), value));
                } else {
                    predicateApplier.builder.predicateGenerator =
                            or(predicateApplier.builder.predicateGenerator,
                                    (cb, root) -> cb.greaterThan(root.get(attribute), value));
                }
            } else {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator = and(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.greaterThan(join.get(attributeOnJoinedEntity), value);
                    });
                } else {
                    predicateApplier.builder.predicateGenerator = or(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.greaterThan(join.get(attributeOnJoinedEntity), value);
                    });
                }
            }
            return new AndOrConnector<>(predicateApplier);
        }

        /**
         * Specifies a <tt>Predicate</tt> that evaluates to <tt>True</tt> if the value of the <tt>attribute</tt> in this
         * <tt>PredicateGenerator</tt> object is greater than or equal to the given <tt>value</tt>.
         *
         * @param value the value to compare with the <tt>attribute</tt> in this <tt>PredicateGenerator</tt> object
         * @return an <tt>AndOrConnector</tt> object to join this <tt>Predicate</tt> with any other <tt>Predicate</tt>
         */
        public final AndOrConnector<T, BUILDER> isGreaterThanOrEqualTo(V value) {
            if (joinedAttribute == null) {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator =
                            and(predicateApplier.builder.predicateGenerator,
                                    (cb, root) -> cb.greaterThanOrEqualTo(root.get(attribute), value));
                } else {
                    predicateApplier.builder.predicateGenerator =
                            or(predicateApplier.builder.predicateGenerator,
                                    (cb, root) -> cb.greaterThanOrEqualTo(root.get(attribute), value));
                }
            } else {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator = and(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.greaterThanOrEqualTo(join.get(attributeOnJoinedEntity), value);
                    });
                } else {
                    predicateApplier.builder.predicateGenerator = or(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.greaterThanOrEqualTo(join.get(attributeOnJoinedEntity), value);
                    });
                }
            }
            return new AndOrConnector<>(predicateApplier);
        }
    }

    /**
     * Specifies a JPA <tt>Predicate</tt> on an <tt>numeric attribute</tt> in the entity (or a <em>joined</em> entity) being queried
     *
     * @param <T>       the type of the entity object
     * @param <J>       the type of the <em>joined</em> entity
     * @param <V>       the type of the value in the <tt>numeric attribute</tt> on which the <tt>Predicate</tt> is applied
     * @param <JOIN>    the type of the attribute representing the <tt>joined</tt> entity
     * @param <BUILDER> type of the <tt>AbstractTypedQueryParamsBuilder</tt> object that is building the <tt>TypedQueryParams</tt> object
     */
    public static class NumericPredicateGenerator<T, J, V extends Number & Comparable<? super V>, JOIN extends Bindable<J> & Attribute<T, ?>, BUILDER extends AbstractTypedQueryParamsBuilder<BUILDER, T>>
            extends ComparablePredicateGenerator<T, J, V, JOIN, BUILDER> {

        /**
         * Constructs an instance with the supplied values
         *
         * @param predicateApplier the <tt>PredicateApplier</tt> object that is adding the predicates
         * @param attribute        the single-valued property representing a column in the entity
         */
        private NumericPredicateGenerator(PredicateApplier<T, BUILDER> predicateApplier, SingularAttribute<T, V> attribute) {
            this(predicateApplier, attribute, null, null);
        }

        /**
         * Constructs an instance with the supplied values
         *
         * @param predicateApplier the <tt>PredicateApplier</tt> object that is adding the predicates
         * @param joinedAttribute  the attribute representing the <tt>joined</tt> entity
         * @param attribute        the single-valued property representing a column in the <em>joined</em> entity
         */
        private NumericPredicateGenerator(PredicateApplier<T, BUILDER> predicateApplier, JOIN joinedAttribute,
                SingularAttribute<J, V> attribute) {
            this(predicateApplier, null, joinedAttribute, attribute);
        }

        /**
         * Constructs an instance with the supplied values
         *
         * @param predicateApplier        the <tt>PredicateApplier</tt> object that is adding the predicates
         * @param attribute               the single-valued property representing a column in the entity
         * @param joinedAttribute         the attribute representing the <tt>joined</tt> entity
         * @param attributeOnJoinedEntity the single-valued property representing a column in the <em>joined</em> entity
         */
        private NumericPredicateGenerator(PredicateApplier<T, BUILDER> predicateApplier, SingularAttribute<T, V> attribute,
                JOIN joinedAttribute, SingularAttribute<J, V> attributeOnJoinedEntity) {
            super(predicateApplier, attribute, joinedAttribute, attributeOnJoinedEntity);
        }

        /**
         * Specifies a <tt>Predicate</tt> that evaluates to <tt>True</tt> if the value of the <tt>attribute</tt> in this
         * <tt>PredicateGenerator</tt> object is <tt>zero</tt>.
         *
         * @return an <tt>AndOrConnector</tt> object to join this <tt>Predicate</tt> with any other <tt>Predicate</tt>
         */
        public final AndOrConnector<T, BUILDER> isZero() {
            if (joinedAttribute == null) {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator =
                            and(predicateApplier.builder.predicateGenerator, (cb, root) -> cb.equal(root.get(attribute), 0));
                } else {
                    predicateApplier.builder.predicateGenerator =
                            or(predicateApplier.builder.predicateGenerator, (cb, root) -> cb.equal(root.get(attribute), 0));
                }
            } else {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator = and(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.equal(join.get(attributeOnJoinedEntity), 0);
                    });
                } else {
                    predicateApplier.builder.predicateGenerator = or(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.equal(join.get(attributeOnJoinedEntity), 0);
                    });
                }
            }
            return new AndOrConnector<>(predicateApplier);
        }

        /**
         * Specifies a <tt>Predicate</tt> that evaluates to <tt>True</tt> if the value of the <tt>attribute</tt> in this
         * <tt>PredicateGenerator</tt> object is <tt>not zero</tt>.
         *
         * @return an <tt>AndOrConnector</tt> object to join this <tt>Predicate</tt> with any other <tt>Predicate</tt>
         */
        public final AndOrConnector<T, BUILDER> isNotZero() {
            if (joinedAttribute == null) {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator =
                            and(predicateApplier.builder.predicateGenerator, (cb, root) -> cb.notEqual(root.get(attribute), 0));
                } else {
                    predicateApplier.builder.predicateGenerator =
                            or(predicateApplier.builder.predicateGenerator, (cb, root) -> cb.notEqual(root.get(attribute), 0));
                }
            } else {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator = and(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.notEqual(join.get(attributeOnJoinedEntity), 0);
                    });
                } else {
                    predicateApplier.builder.predicateGenerator = or(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.notEqual(join.get(attributeOnJoinedEntity), 0);
                    });
                }
            }
            return new AndOrConnector<>(predicateApplier);
        }

        /**
         * Specifies a <tt>Predicate</tt> that evaluates to <tt>True</tt> if the value of the <tt>attribute</tt> in this
         * <tt>PredicateGenerator</tt> object is <tt>positive</tt>.
         *
         * @return an <tt>AndOrConnector</tt> object to join this <tt>Predicate</tt> with any other <tt>Predicate</tt>
         */
        public final AndOrConnector<T, BUILDER> isPositive() {
            if (joinedAttribute == null) {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator =
                            and(predicateApplier.builder.predicateGenerator, (cb, root) -> cb.gt(root.get(attribute), 0));
                } else {
                    predicateApplier.builder.predicateGenerator =
                            or(predicateApplier.builder.predicateGenerator, (cb, root) -> cb.gt(root.get(attribute), 0));
                }
            } else {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator = and(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.gt(join.get(attributeOnJoinedEntity), 0);
                    });
                } else {
                    predicateApplier.builder.predicateGenerator = or(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.gt(join.get(attributeOnJoinedEntity), 0);
                    });
                }
            }
            return new AndOrConnector<>(predicateApplier);
        }

        /**
         * Specifies a <tt>Predicate</tt> that evaluates to <tt>True</tt> if the value of the <tt>attribute</tt> in this
         * <tt>PredicateGenerator</tt> object is <tt>negative</tt>.
         *
         * @return an <tt>AndOrConnector</tt> object to join this <tt>Predicate</tt> with any other <tt>Predicate</tt>
         */
        public final AndOrConnector<T, BUILDER> isNegative() {
            if (joinedAttribute == null) {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator =
                            and(predicateApplier.builder.predicateGenerator, (cb, root) -> cb.lt(root.get(attribute), 0));
                } else {
                    predicateApplier.builder.predicateGenerator =
                            or(predicateApplier.builder.predicateGenerator, (cb, root) -> cb.lt(root.get(attribute), 0));
                }
            } else {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator = and(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.lt(join.get(attributeOnJoinedEntity), 0);
                    });
                } else {
                    predicateApplier.builder.predicateGenerator = or(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.lt(join.get(attributeOnJoinedEntity), 0);
                    });
                }
            }
            return new AndOrConnector<>(predicateApplier);
        }
    }

    /**
     * Specifies a JPA <tt>Predicate</tt> on an <tt>Boolean attribute</tt> in the entity (or a <em>joined</em> entity) being queried
     *
     * @param <T>       the type of the entity object
     * @param <J>       the type of the <em>joined</em> entity
     * @param <JOIN>    the type of the attribute representing the <tt>joined</tt> entity
     * @param <BUILDER> type of the <tt>AbstractTypedQueryParamsBuilder</tt> object that is building the <tt>TypedQueryParams</tt> object
     */
    public static class BooleanPredicateGenerator<T, J, JOIN extends Bindable<J> & Attribute<T, ?>, BUILDER extends AbstractTypedQueryParamsBuilder<BUILDER, T>>
            extends ComparablePredicateGenerator<T, J, Boolean, JOIN, BUILDER> {

        /**
         * Constructs an instance with the supplied values
         *
         * @param predicateApplier the <tt>PredicateApplier</tt> object that is adding the predicates
         * @param attribute        the single-valued property representing a column in the entity
         */
        private BooleanPredicateGenerator(PredicateApplier<T, BUILDER> predicateApplier,
                SingularAttribute<T, Boolean> attribute) {
            this(predicateApplier, attribute, null, null);
        }

        /**
         * Constructs an instance with the supplied values
         *
         * @param predicateApplier the <tt>PredicateApplier</tt> object that is adding the predicates
         * @param joinedAttribute  the attribute representing the <tt>joined</tt> entity
         * @param attribute        the single-valued property representing a column in the <em>joined</em> entity
         */
        private BooleanPredicateGenerator(PredicateApplier<T, BUILDER> predicateApplier, JOIN joinedAttribute,
                SingularAttribute<J, Boolean> attribute) {
            this(predicateApplier, null, joinedAttribute, attribute);
        }

        /**
         * Constructs an instance with the supplied values
         *
         * @param predicateApplier        the <tt>PredicateApplier</tt> object that is adding the predicates
         * @param attribute               the single-valued property representing a column in the entity
         * @param joinedAttribute         the attribute representing the <tt>joined</tt> entity
         * @param attributeOnJoinedEntity the single-valued property representing a column in the <em>joined</em> entity
         */
        private BooleanPredicateGenerator(PredicateApplier<T, BUILDER> predicateApplier, SingularAttribute<T, Boolean> attribute,
                JOIN joinedAttribute, SingularAttribute<J, Boolean> attributeOnJoinedEntity) {
            super(predicateApplier, attribute, joinedAttribute, attributeOnJoinedEntity);
        }

        /**
         * Specifies a <tt>Predicate</tt> that evaluates to <tt>True</tt> if the value of the <tt>attribute</tt> in this
         * <tt>PredicateGenerator</tt> object is <tt>True</tt>.
         *
         * @return an <tt>AndOrConnector</tt> object to join this <tt>Predicate</tt> with any other <tt>Predicate</tt>
         */
        public final AndOrConnector<T, BUILDER> isTrue() {
            if (joinedAttribute == null) {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator =
                            and(predicateApplier.builder.predicateGenerator, (cb, root) -> cb.isTrue(root.get(attribute)));
                } else {
                    predicateApplier.builder.predicateGenerator =
                            or(predicateApplier.builder.predicateGenerator, (cb, root) -> cb.isTrue(root.get(attribute)));
                }
            } else {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator = and(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.isTrue(join.get(attributeOnJoinedEntity));
                    });
                } else {
                    predicateApplier.builder.predicateGenerator = or(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.isTrue(join.get(attributeOnJoinedEntity));
                    });
                }
            }
            return new AndOrConnector<>(predicateApplier);
        }

        /**
         * Specifies a <tt>Predicate</tt> that evaluates to <tt>True</tt> if the value of the <tt>attribute</tt> in this
         * <tt>PredicateGenerator</tt> object is <tt>False</tt>.
         *
         * @return an <tt>AndOrConnector</tt> object to join this <tt>Predicate</tt> with any other <tt>Predicate</tt>
         */
        public final AndOrConnector<T, BUILDER> isFalse() {
            if (joinedAttribute == null) {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator =
                            and(predicateApplier.builder.predicateGenerator, (cb, root) -> cb.isFalse(root.get(attribute)));
                } else {
                    predicateApplier.builder.predicateGenerator =
                            or(predicateApplier.builder.predicateGenerator, (cb, root) -> cb.isFalse(root.get(attribute)));
                }
            } else {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator = and(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.isFalse(join.get(attributeOnJoinedEntity));
                    });
                } else {
                    predicateApplier.builder.predicateGenerator = or(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.isFalse(join.get(attributeOnJoinedEntity));
                    });
                }
            }
            return new AndOrConnector<>(predicateApplier);
        }
    }

    /**
     * Specifies a JPA <tt>Predicate</tt> on an <tt>String attribute</tt> in the entity (or a <em>joined</em> entity) being queried
     *
     * @param <T>       the type of the entity object
     * @param <J>       the type of the <em>joined</em> entity
     * @param <JOIN>    the type of the attribute representing the <tt>joined</tt> entity
     * @param <BUILDER> type of the <tt>AbstractTypedQueryParamsBuilder</tt> object that is building the <tt>TypedQueryParams</tt> object
     */
    public static class StringPredicateGenerator<T, J, JOIN extends Bindable<J> & Attribute<T, ?>, BUILDER extends AbstractTypedQueryParamsBuilder<BUILDER, T>>
            extends ComparablePredicateGenerator<T, J, String, JOIN, BUILDER> {

        /**
         * Constructs an instance with the supplied values
         *
         * @param predicateApplier the <tt>PredicateApplier</tt> object that is adding the predicates
         * @param attribute        the single-valued property representing a column in the entity
         */
        private StringPredicateGenerator(PredicateApplier<T, BUILDER> predicateApplier, SingularAttribute<T, String> attribute) {
            this(predicateApplier, attribute, null, null);
        }

        /**
         * Constructs an instance with the supplied values
         *
         * @param predicateApplier the <tt>PredicateApplier</tt> object that is adding the predicates
         * @param joinedAttribute  the attribute representing the <tt>joined</tt> entity
         * @param attribute        the single-valued property representing a column in the <em>joined</em> entity
         */
        private StringPredicateGenerator(PredicateApplier<T, BUILDER> predicateApplier, JOIN joinedAttribute,
                SingularAttribute<J, String> attribute) {
            this(predicateApplier, null, joinedAttribute, attribute);
        }

        /**
         * Constructs an instance with the supplied values
         *
         * @param predicateApplier        the <tt>PredicateApplier</tt> object that is adding the predicates
         * @param attribute               the single-valued property representing a column in the entity
         * @param joinedAttribute         the attribute representing the <tt>joined</tt> entity
         * @param attributeOnJoinedEntity the single-valued property representing a column in the <em>joined</em> entity
         */
        private StringPredicateGenerator(PredicateApplier<T, BUILDER> predicateApplier, SingularAttribute<T, String> attribute,
                JOIN joinedAttribute, SingularAttribute<J, String> attributeOnJoinedEntity) {
            super(predicateApplier, attribute, joinedAttribute, attributeOnJoinedEntity);
        }

        /**
         * Specifies a <tt>Predicate</tt> that evaluates to <tt>True</tt> if the value of the <tt>attribute</tt> in this
         * <tt>PredicateGenerator</tt> object <tt>starts</tt> with the given <tt>value</tt>.
         *
         * @param value the value to compare with the <tt>attribute</tt> in this <tt>PredicateGenerator</tt> object
         * @return an <tt>AndOrConnector</tt> object to join this <tt>Predicate</tt> with any other <tt>Predicate</tt>
         */
        public final AndOrConnector<T, BUILDER> startsWith(String value) {
            return isLike(value + "%");
        }

        /**
         * Specifies a <tt>Predicate</tt> that evaluates to <tt>True</tt> if the value of the <tt>attribute</tt> in this
         * <tt>PredicateGenerator</tt> object <tt>contains</tt> the given <tt>value</tt>.
         *
         * @param value the value to compare with the <tt>attribute</tt> in this <tt>PredicateGenerator</tt> object
         * @return an <tt>AndOrConnector</tt> object to join this <tt>Predicate</tt> with any other <tt>Predicate</tt>
         */
        public final AndOrConnector<T, BUILDER> contains(String value) {
            return isLike("%" + value + "%");
        }

        /**
         * Specifies a <tt>Predicate</tt> that evaluates to <tt>True</tt> if the value of the <tt>attribute</tt> in this
         * <tt>PredicateGenerator</tt> object <tt>ends</tt> with the given <tt>value</tt>.
         *
         * @param value the value to compare with the <tt>attribute</tt> in this <tt>PredicateGenerator</tt> object
         * @return an <tt>AndOrConnector</tt> object to join this <tt>Predicate</tt> with any other <tt>Predicate</tt>
         */
        public final AndOrConnector<T, BUILDER> endsWith(String value) {
            return isLike("%" + value);
        }

        /**
         * Specifies a <tt>Predicate</tt> that evaluates to <tt>True</tt> if the value of the <tt>attribute</tt> in this
         * <tt>PredicateGenerator</tt> object is <tt>like</tt><em>(as in the SQL operator <b>like</b>)</em> the given <tt>value</tt>.
         * <p>The formal argument <tt>value</tt> is passed to the LIKE operator in the SQL query without any modifications. Hence
         * the users should pass this <tt>value</tt> with the necessary SQL wildcard characters in the relevant position.
         *
         * @param value the value to compare with the <tt>attribute</tt> in this <tt>PredicateGenerator</tt> object
         * @return an <tt>AndOrConnector</tt> object to join this <tt>Predicate</tt> with any other <tt>Predicate</tt>
         * @see #contains(String)
         * @see #endsWith(String)
         * @see #startsWith(String)
         */
        public final AndOrConnector<T, BUILDER> isLike(String value) {
            if (joinedAttribute == null) {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator =
                            and(predicateApplier.builder.predicateGenerator, (cb, root) -> cb.like(root.get(attribute), value));
                } else {
                    predicateApplier.builder.predicateGenerator =
                            or(predicateApplier.builder.predicateGenerator, (cb, root) -> cb.like(root.get(attribute), value));
                }
            } else {
                if (predicateApplier.builder.predicateConnector.equals("and")) {
                    predicateApplier.builder.predicateGenerator = and(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.like(join.get(attributeOnJoinedEntity), value);
                    });
                } else {
                    predicateApplier.builder.predicateGenerator = or(predicateApplier.builder.predicateGenerator, (cb, root) -> {
                        final Join<T, J> join = getJoinPath(joinedAttribute, root);
                        return cb.like(join.get(attributeOnJoinedEntity), value);
                    });
                }
            }
            return new AndOrConnector<>(predicateApplier);
        }
    }

    /**
     * A <em>connector</em> of <tt>Predicate</tt> objects.
     *
     * @param <T>       the type of the entity object
     * @param <BUILDER> type of the <tt>AbstractTypedQueryParamsBuilder</tt> object that is building the <tt>TypedQueryParams</tt> object
     */
    public static class AndOrConnector<T, BUILDER extends AbstractTypedQueryParamsBuilder<BUILDER, T>> {

        /**
         * the <tt>PredicateApplier</tt> that creates this <tt>AndOrConnector</tt> object
         **/
        private final PredicateApplier<T, BUILDER> predicateApplier;

        /**
         * Constructs an instance with the supplied <tt>predicateApplier</tt>
         *
         * @param predicateApplier <tt>PredicateApplier</tt> that creates this <tt>AndOrConnector</tt> object
         */
        private AndOrConnector(PredicateApplier<T, BUILDER> predicateApplier) {
            this.predicateApplier = predicateApplier;
        }

        /**
         * Specifies a logical OR between two predicates
         *
         * @return the <tt>PredicateApplier</tt> that created this <tt>AndOrConnector</tt> object
         */
        public PredicateApplier<T, BUILDER> or() {
            predicateApplier.builder.predicateConnector = "or";
            return predicateApplier;
        }

        /**
         * Specifies a logical AND between two predicates
         *
         * @return the <tt>PredicateApplier</tt> that created this <tt>AndOrConnector</tt> object
         */
        public PredicateApplier<T, BUILDER> and() {
            predicateApplier.builder.predicateConnector = "and";
            return predicateApplier;
        }

        /**
         * Returns the <tt>AbstractTypedQueryParamsBuilder</tt> object that is building the <tt>TypedQueryParams</tt> object
         *
         * @return the <tt>AbstractTypedQueryParamsBuilder</tt> object that is building the <tt>TypedQueryParams</tt> object
         */
        public BUILDER apply() {
            return predicateApplier.builder;
        }
    }

    /**
     * Builder of a <tt>TypedQueryParams</tt> object
     *
     * @param <T> type of the entity for which the <tt>TypedQueryParams</tt> object is created
     * @see TypedQueryParams
     * @see TypedQueryParamsSubmitter
     */
    public static class TypedQueryParamsBuilder<T> extends AbstractTypedQueryParamsBuilder<TypedQueryParamsBuilder<T>, T> {

        /**
         * Returns a new <tt>TypedQueryParamsBuilder</tt> instance
         *
         * @param <T> type of the entity for which the <tt>TypedQueryParams</tt> object is created
         * @return a new <tt>TypedQueryParamsBuilder</tt> instance
         */
        public static <T> TypedQueryParamsBuilder<T> create() {
            return new TypedQueryParamsBuilder<>();
        }

        /**
         * Returns a new <tt>TypedQueryParams</tt> object using the predicates, joins and order bys set in this builder
         *
         * @return a new <tt>TypedQueryParams</tt> object using the predicates, joins and order bys set in this builder
         */
        public TypedQueryParams<T> build() {
            return new TypedQueryParams<>(pathGenerator,
                    Optional.ofNullable(predicateGenerator)
                            // If no predicates have been set then use an "always true" predicate to include all results.
                            // This is to avoid the need for null checks while applying this predicate by the query.
                            .orElse((cb, root) -> cb.isTrue(cb.literal(true))),
                    orderByGenerators);
        }
    }

    /**
     * Builds a <tt>TypedQueryParams</tt> object and submits it to the database via a <tt>BaseRepository</tt> instance
     *
     * @param <T> type of the entity for which the <tt>TypedQueryParams</tt> object is created
     * @param <R> type of the result obtained after submitting the <tt>TypedQueryParams</tt> object
     * @see TypedQueryParams
     * @see TypedQueryParamsBuilder
     */
    public static class TypedQueryParamsSubmitter<T, R>
            extends AbstractTypedQueryParamsBuilder<TypedQueryParamsSubmitter<T, R>, T> {

        /**
         * the <tt>Function</tt> that returns a result after submitting the <tt>TypedQueryParams</tt> object to the database
         **/
        private final Function<TypedQueryParams<T>, R> submitAction;

        /**
         * Constructs an instance with the given <tt>submitAction</tt>
         *
         * @param submitAction the <tt>Function</tt> that returns a result after submitting the <tt>TypedQueryParams</tt> object to the database
         */
        private TypedQueryParamsSubmitter(Function<TypedQueryParams<T>, R> submitAction) {
            this.submitAction = submitAction;
        }

        /**
         * Returns a new <tt>TypedQueryParamsSubmitter</tt> instance
         *
         * @param submitAction the <tt>Function</tt> that returns a result after submitting the <tt>TypedQueryParams</tt> object to the database
         * @param <T>          type of the entity for which the <tt>TypedQueryParams</tt> object is created
         * @param <R>          type of the result obtained after submitting the <tt>TypedQueryParams</tt> object to the database
         * @return a new <tt>TypedQueryParamsSubmitter</tt> instance
         */
        public static <T, R> TypedQueryParamsSubmitter<T, R> create(Function<TypedQueryParams<T>, R> submitAction) {
            return new TypedQueryParamsSubmitter<>(submitAction);
        }

        /**
         * Returns the result after submitting the <tt>TypedQueryParams</tt> object to the database
         *
         * @return the result after submitting the <tt>TypedQueryParams</tt> object to the database
         */
        public R submit() {
            final TypedQueryParams<T> queryParams = new TypedQueryParams<>(pathGenerator,
                    Optional.ofNullable(predicateGenerator)
                            // If no predicates have been set then use an "always true" predicate to include all results.
                            // This is to avoid the need for null checks while applying this predicate by the query.
                            .orElse((cb, root) -> cb.isTrue(cb.literal(true))),
                    orderByGenerators);
            return submitAction.apply(queryParams);
        }
    }
}
