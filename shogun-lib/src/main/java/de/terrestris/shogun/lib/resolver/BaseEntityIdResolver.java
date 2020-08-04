package de.terrestris.shogun.lib.resolver;

import com.fasterxml.jackson.annotation.ObjectIdGenerator.IdKey;
import com.fasterxml.jackson.annotation.SimpleObjectIdResolver;
import de.terrestris.shogun.lib.model.BaseEntity;
import de.terrestris.shogun.lib.service.BaseService;
import de.terrestris.shogun.lib.util.ApplicationContextProvider;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;

import java.util.Optional;

@Log4j2
public abstract class BaseEntityIdResolver<E extends BaseEntity, S extends BaseService> extends SimpleObjectIdResolver {

    protected S service;

    /**
     * Default Constructor that injects beans automatically.
     */
    protected BaseEntityIdResolver() {
        // As subclasses of this class are used in the resolver property of an
        // JsonIdentityInfo annotation, we cannot easily autowire components
        // (like the service for the current). For that reason, we use this
        // helper method to process the injection of the services
        ApplicationContext a = ApplicationContextProvider.getApplicationContext();
        a.getAutowireCapableBeanFactory().autowireBean(this);
        SpringBeanAutowiringSupport.processInjectionBasedOnCurrentContext(this);
    }

    @Override
    public void bindItem(IdKey id, Object ob) {
        super.bindItem(id, ob);
    }

    @Override
    public E resolveId(IdKey idKey) {
        try {
            if (idKey.key instanceof Long) {
                final Long id = (Long) idKey.key;
                // we only "load" the entity to follow a lazy approach, i.e.
                // requests to the database will only be queried if any properties
                // of the entity will (later) be accessed (which may already
                // the case when jackson is doing some magic)
                Optional<E> optional = service.findOne(id);
                if (optional.isPresent()) {
                    return optional.get();
                } else {
                    log.error("Could not find entity with id {}", id);
                    return null;
                }
            } else {
                throw new Exception("ID is not of type Long.");
            }
        } catch (Exception e) {
            log.error("Could not resolve object by ID: {}", e.getMessage());
            log.trace("Full stack trace: ", e);
            return null;
        }
    }

    @Override
    public BaseEntityIdResolver newForDeserialization(Object context) {
        try {
            return getClass().newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            log.error("Error instantiating ObjectIdResolver, {}",  e.getMessage());
        }
        return null;
    }

    public boolean canUseFor(final BaseEntityIdResolver resolverType) {
        return false;
    }

    /**
     * @return the service
     */
    public S getService() {
        return service;
    }

    /**
     * Has to be implemented by subclasses to autowire and set the correct
     * service class.
     *
     * @param service the service to set
     */
    public abstract void setService(S service);
}
