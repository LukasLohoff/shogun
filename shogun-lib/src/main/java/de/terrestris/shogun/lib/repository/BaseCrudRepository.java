package de.terrestris.shogun.lib.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.history.RevisionRepository;

import javax.persistence.QueryHint;
import java.util.List;

@NoRepositoryBean
public interface BaseCrudRepository<T, ID> extends RevisionRepository<T, ID, Integer>, PagingAndSortingRepository<T, ID> {

    @QueryHints(@QueryHint(name = org.hibernate.annotations.QueryHints.CACHEABLE, value = "true"))
    List<T> findAll();

    @Query(nativeQuery = true, value = "select m from #{#entityName} m where has_permission(?#{ principal?.context.token.subject }, m.id, ARRAY['e8bc650f-c577-4b63-a6bf-90a70482c25a'], 'de.terrestris.progemis.model.Application')")
    @QueryHints(@QueryHint(name = org.hibernate.annotations.QueryHints.CACHEABLE, value = "true"))
    Page<T> findAll(Pageable pageable);

}
