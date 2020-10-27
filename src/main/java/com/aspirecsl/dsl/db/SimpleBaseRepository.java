package com.aspirecsl.dsl.db;

import java.util.List;
import java.util.function.Consumer;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.CriteriaUpdate;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import javax.persistence.metamodel.SingularAttribute;

import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Collections.unmodifiableList;
import static org.springframework.transaction.annotation.Propagation.REQUIRED;

/**
 * A simple implementation of the the <tt>BaseRepository</tt> interface.
 *
 * @author anoopr
 */
public class SimpleBaseRepository<T> extends SimpleJpaRepository<T, Long>
        implements BaseRepository<T> {

    /**
     * the <tt>Class</tt> denoting the type of the entity object representing a database table
     **/
    private final Class<T> entityClass;

    /**
     * the ID attribute of the entity managed by this repository
     **/
    private final SingularAttribute<? super T, ?> idAttribute;

    /**
     * the entity manager from the persistence context
     **/
    private final EntityManager entityManager;

    /**
     * Constructs an instance with the supplied JPA <tt>entityInformation</tt> and <tt>entityManager</tt>
     *
     * @param entityInformation the JPA entity information object to be set in the <tt>SimpleJpaRepository</tt> object
     * @param entityManager     the entity manager from the persistence context
     */
    public SimpleBaseRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
        this.entityClass = entityInformation.getJavaType();
        idAttribute = entityInformation.getIdAttribute();
    }

    @Override
    public final T findOneUsing(TypedQueryParams<T> queryParams) {
        try {
            final CriteriaQuery<T> criteriaQuery = buildSelectQuery(entityManager, queryParams);
            return entityManager.createQuery(criteriaQuery).getSingleResult();
        } catch (NoResultException ignored) {
            return null;
        }
    }

    @Override
    public final List<T> findAllUsing(TypedQueryParams<T> queryParams) {
        final CriteriaQuery<T> criteriaQuery = buildSelectQuery(entityManager, queryParams);
        return unmodifiableList(entityManager.createQuery(criteriaQuery).getResultList());
    }

    @Override
    @Transactional(propagation = REQUIRED)
    public int deleteUsing(TypedQueryParams<T> queryParams) {
        entityManager.flush();
        entityManager.clear();
        final CriteriaDelete<T> criteriaDelete = buildDeleteQuery(entityManager, queryParams);
        return entityManager.createQuery(criteriaDelete).executeUpdate();
    }

    @Transactional(propagation = REQUIRED)
    public int updateUsing(TypedQueryParams<T> queryParams, List<Consumer<CriteriaUpdate<T>>> updates) {
        entityManager.flush();
        entityManager.clear();
        final CriteriaUpdate<T> criteriaUpdate = buildUpdateQuery(entityManager, queryParams, updates);
        return entityManager.createQuery(criteriaUpdate).executeUpdate();
    }

    @Override
    public final long count() {
        final CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        final CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        cq.select(cb.count(cq.from(entityClass)));
        return entityManager.createQuery(cq).getSingleResult();
    }

    @Override
    public long checkpoint() {
        final CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        final CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        final Root<T> root = cq.from(entityClass);
        cq.select(cb.coalesce(cb.max(root.get(idAttribute.getName())), 0L));
        return entityManager.createQuery(cq).getSingleResult();
    }

    /**
     * Returns a <tt>CriteriaQuery</tt> to select an entity (or entities) from the database
     *
     * @param em          the entity manager from the persistence context
     * @param queryParams the query params to match the entity (or entities)
     * @return a <tt>CriteriaQuery</tt> to select an entity (or entities) from the database
     */
    private CriteriaQuery<T> buildSelectQuery(EntityManager em, TypedQueryParams<T> queryParams) {
        final CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        final CriteriaQuery<T> criteriaQuery = criteriaBuilder.createQuery(entityClass);
        final From<?, T> path = queryParams.finalPath(criteriaQuery.from(entityClass));
        return criteriaQuery
                .select(path)
                .where(queryParams.predicate(criteriaBuilder, path))
                .orderBy(queryParams.orderBy(criteriaBuilder, path));
    }

    /**
     * Returns a <tt>CriteriaDelete</tt> query to delete an entity (or entities) from the database
     *
     * @param em          the entity manager from the persistence context
     * @param queryParams the query params to match the entity (or entities)
     * @return a <tt>CriteriaDelete</tt> query to delete an entity (or entities) from the database
     */
    private CriteriaDelete<T> buildDeleteQuery(EntityManager em, TypedQueryParams<T> queryParams) {
        final CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        final CriteriaDelete<T> criteriaDelete = criteriaBuilder.createCriteriaDelete(entityClass);
        final Root<T> root = criteriaDelete.from(entityClass);
        final Subquery<T> subQuery = criteriaDelete.subquery(entityClass);
        final Root<T> subQueryRoot = subQuery.from(entityClass);
        final From<?, T> subQueryPath = queryParams.finalPath(subQueryRoot);
        subQuery.select(subQueryRoot);
        return criteriaDelete
                .where(root
                        .in(subQuery
                                .where(queryParams.predicate(criteriaBuilder, subQueryPath))));
    }

    /**
     * Returns a <tt>CriteriaUpdate</tt> query to update an entity (or entities) from the database
     *
     * @param em          the entity manager from the persistence context
     * @param queryParams the query params to match the entity (or entities)
     * @param updates     the list of updates to be applied to a matching entity (or entities)
     * @return a <tt>CriteriaUpdate</tt> query to update an entity (or entities) from the database
     */
    private CriteriaUpdate<T> buildUpdateQuery(EntityManager em, TypedQueryParams<T> queryParams,
            List<Consumer<CriteriaUpdate<T>>> updates) {
        final CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        final CriteriaUpdate<T> criteriaUpdate = criteriaBuilder.createCriteriaUpdate(entityClass);
        final Root<T> root = criteriaUpdate.from(entityClass);
        updates.forEach(update -> update.accept(criteriaUpdate));
        final Subquery<T> subQuery = criteriaUpdate.subquery(entityClass);
        final Root<T> subQueryRoot = subQuery.from(entityClass);
        final From<?, T> subQueryPath = queryParams.finalPath(subQueryRoot);
        subQuery.select(subQueryRoot);
        return criteriaUpdate
                .where(root
                        .in(subQuery
                                .where(queryParams.predicate(criteriaBuilder, subQueryPath))));
    }
}
