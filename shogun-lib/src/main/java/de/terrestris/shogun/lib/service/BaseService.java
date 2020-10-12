package de.terrestris.shogun.lib.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.terrestris.shogun.lib.enumeration.PermissionCollectionType;
import de.terrestris.shogun.lib.model.BaseEntity;
import de.terrestris.shogun.lib.repository.BaseCrudRepository;
import de.terrestris.shogun.lib.security.SecurityContextUtil;
import de.terrestris.shogun.lib.security.access.BasePermissionEvaluator;
import de.terrestris.shogun.lib.service.security.permission.GroupInstancePermissionService;
import de.terrestris.shogun.lib.service.security.permission.UserInstancePermissionService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.history.Revision;
import org.springframework.data.history.Revisions;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class BaseService<T extends BaseCrudRepository<S, Long> & JpaSpecificationExecutor<S>, S extends BaseEntity> {

    protected final Logger LOG = LogManager.getLogger(getClass());

    @Autowired
    protected T repository;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    protected UserInstancePermissionService userInstancePermissionService;

    @Autowired
    protected BasePermissionEvaluator basePermissionEvaluator;

    @Autowired
    protected GroupInstancePermissionService groupInstancePermissionService;

    @Autowired
    protected SecurityContextUtil securityContextUtil;

    @PostFilter("hasRole('ROLE_ADMIN') or hasPermission(filterObject, 'READ')")
    @Transactional(readOnly = true)
    public List<S> findAll() {
        return (List<S>) repository.findAll();
    }

    // @PostFilter("hasRole('ROLE_ADMIN') or hasPermission(filterObject, 'READ')")
//    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Transactional(readOnly = true)
    public Page<S> findAllWithWorkaround(Pageable pageable) {
        Page<S> pageResult = (Page<S>) repository.findAll(pageable);
        if (securityContextUtil.hasRole("ROLE_ADMIN")) {
           return pageResult;
        }
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication authentication = context.getAuthentication();
        List<S> foundResults = new ArrayList<>();
        List<S> filteredResults = fetchUntilPageSizeIsReached(pageResult.getContent(), foundResults, pageResult.getTotalElements(), pageable, authentication);
        return new PageImpl<>(filteredResults, pageable, filteredResults.size());
    }

    // security check is done on repository
    @Transactional(readOnly = true)
    public Page<S> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @PostFilter("hasRole('ROLE_ADMIN') or hasPermission(filterObject, 'READ')")
    @Transactional(readOnly = true)
    public List<S> findAllBy(Specification specification) {
        return (List<S>) repository.findAll(specification);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Transactional(readOnly = true)
    public Page<S> findAllBy(Specification specification, Pageable pageable) {
        return (Page<S>) repository.findAll(specification, pageable);
    }

    @PostAuthorize("hasRole('ROLE_ADMIN') or hasPermission(returnObject.orElse(null), 'READ')")
    @Transactional(readOnly = true)
    public Optional<S> findOne(Long id) {
        return repository.findById(id);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') or hasPermission(#entity, 'READ')")
    @Transactional(readOnly = true)
    public Revisions<Integer, S> findRevisions(S entity) {
        return repository.findRevisions(entity.getId());
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') or hasPermission(#entity, 'READ')")
    @Transactional(readOnly = true)
    public Optional<Revision<Integer, S>> findRevision(S entity, Integer rev) {
        return repository.findRevision(entity.getId(), rev);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') or hasPermission(#entity, 'READ')")
    @Transactional(readOnly = true)
    public Optional<Revision<Integer, S>> findLastChangeRevision(S entity) {
        return repository.findLastChangeRevision(entity.getId());
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') or hasPermission(#entity, 'CREATE')")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public S create(S entity) {
        S persistedEntity = repository.save(entity);

        userInstancePermissionService.setPermission(persistedEntity, PermissionCollectionType.ADMIN);

        return persistedEntity;
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') or hasPermission(#entity, 'UPDATE')")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public S update(Long id, S entity) throws IOException {
        Optional<S> persistedEntity = repository.findById(id);

        JsonNode jsonObject = objectMapper.valueToTree(entity);
        S updatedEntity = objectMapper.readerForUpdating(persistedEntity.get()).readValue(jsonObject);

        return repository.save(updatedEntity);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') or hasPermission(#entity, 'DELETE')")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void delete(S entity) {
        userInstancePermissionService.deleteAllForEntity(entity);

        groupInstancePermissionService.deleteAllForEntity(entity);

        repository.delete(entity);
    }

    // currently not needed
    public List<S> fetchUntilPageSizeIsReached(List<S> pageResult, List<S> filteredResults, long totalResults, Pageable pageable, Authentication auth) {
        if (pageable.getOffset() >= totalResults) {
            LOG.info("Last page {} reached. Returning {} filtered results, of {} total", pageable.getPageNumber(), pageResult.size(), totalResults);
            return filteredResults;
        }

        LOG.info("Filtering {} new elements", pageResult.size());
        List<S> filteredPageResult = pageResult.stream()
            .filter(pr -> basePermissionEvaluator.hasPermission(auth, pr, "READ"))
            .collect(Collectors.toList());

        if (filteredPageResult.size() == pageResult.size()) {
            // page filled, return results
            return pageResult;
        } else {
            // fetch more results to fill page
            Pageable nextPage = pageable.next();
            List<S> nextPageResult = repository.findAll(nextPage).getContent();
            filteredResults.addAll(filteredPageResult);
            LOG.info("Not enough results to fill page, checking for more results in next page {}", nextPage.getPageNumber());
            return fetchUntilPageSizeIsReached(nextPageResult, filteredResults, totalResults, nextPage, auth);
        }
    }
}
