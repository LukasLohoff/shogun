package de.terrestris.shogun.lib.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @QueryHints(@QueryHint(name = org.hibernate.annotations.QueryHints.CACHEABLE, value = "true"))
    Page<T> findAll(Pageable pageable);

    // @PostFilter("hasRole('ROLE_ADMIN') or hasPermission(filterObject, 'READ')")
    //@Query("select m from #{#entityName} m where 1= ?#{ hasRole('ROLE_ADMIN') ? 1 : 0 }")
//    @Query("select m from #{#entityName} m where 1 = ?#{ (hasRole('ROLE_ADMIN') or hasPermission(filterObject, 'READ')) ? 1 : 0 }")
//    @Query("select m from #{#entityName} m where m.id = 1 =  ?#{ (hasRole('ROLE_ADMIN') or hasPermission(m, ,'READ')) ? 1 : 0 }")
    // Page<T> findAll(Pageable pageable);
}
