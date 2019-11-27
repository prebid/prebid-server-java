package org.prebid.server.spring.config;

import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.ContextRunner;
import org.prebid.server.vertx.Initializable;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.util.List;

/**
 * Some services are initialized after context is fully populated to overcome deadlock that may happen due to lazy
 * creation of {@link HttpClient} instances.
 * <p>
 * Since {@link HttpClient} bean is defined as scoped proxy its instances are created lazily on demand. This
 * leads to a situation when other beans using {@link HttpClient} during initialization trigger creation of
 * {@link HttpClient}s dependencies like {@link Metrics} causing deadlock on this resource:
 * org.springframework.beans.factory.support.DefaultSingletonBeanRegistry#singletonObjects
 * <p>
 * Having services that depend on {@link HttpClient} in their initialization actions initialized after dependency
 * tree is fully constructed ensures that subsequent {@link HttpClient} instance creation will not happen in the
 * middle of another bean creation process.
 */
@Configuration
public class InitializationConfiguration {

    @Autowired
    private ContextRunner contextRunner;

    @Autowired
    private List<Initializable> initializables;

    @EventListener(ContextRefreshedEvent.class)
    public void initializeServices() {
        contextRunner.runOnServiceContext(future -> {
            initializables.forEach(Initializable::initialize);
            future.complete();
        });
    }
}
