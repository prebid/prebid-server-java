package org.prebid.server.spring.config;

import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.service.HttpPeriodicRefreshService;
import org.prebid.server.settings.service.JdbcPeriodicRefreshService;
import org.prebid.server.vertx.ContextRunner;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

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
    private ObjectProvider<CurrencyConversionService> currencyConversionServiceProvider;

    @Autowired
    @Qualifier("httpPeriodicRefreshService")
    private ObjectProvider<HttpPeriodicRefreshService> httpPeriodicRefreshServiceProvider;

    @Autowired
    @Qualifier("ampHttpPeriodicRefreshService")
    private ObjectProvider<HttpPeriodicRefreshService> ampHttpPeriodicRefreshServiceProvider;

    @Autowired
    @Qualifier("jdbcPeriodicRefreshService")
    private ObjectProvider<JdbcPeriodicRefreshService> jdbcPeriodicRefreshServiceProvider;

    @Autowired
    @Qualifier("ampJdbcPeriodicRefreshService")
    private ObjectProvider<JdbcPeriodicRefreshService> ampJdbcPeriodicRefreshServiceProvider;

    @EventListener(ContextRefreshedEvent.class)
    public void initializeServices() {

        final CurrencyConversionService currencyConversionService =
                currencyConversionServiceProvider.getIfAvailable();
        final HttpPeriodicRefreshService httpPeriodicRefreshService =
                httpPeriodicRefreshServiceProvider.getIfAvailable();
        final HttpPeriodicRefreshService ampHttpPeriodicRefreshService =
                ampHttpPeriodicRefreshServiceProvider.getIfAvailable();
        final JdbcPeriodicRefreshService jdbcPeriodicRefreshService =
                jdbcPeriodicRefreshServiceProvider.getIfAvailable();
        final JdbcPeriodicRefreshService ampJdbcPeriodicRefreshService =
                ampJdbcPeriodicRefreshServiceProvider.getIfAvailable();

        contextRunner.runOnServiceContext(future -> {
            if (currencyConversionService != null) {
                currencyConversionService.initialize();
            }
            if (httpPeriodicRefreshService != null) {
                httpPeriodicRefreshService.initialize();
            }
            if (ampHttpPeriodicRefreshService != null) {
                ampHttpPeriodicRefreshService.initialize();
            }
            if (jdbcPeriodicRefreshService != null) {
                jdbcPeriodicRefreshService.initialize();
            }
            if (ampJdbcPeriodicRefreshService != null) {
                ampJdbcPeriodicRefreshService.initialize();
            }

            future.complete();
        });
    }
}
