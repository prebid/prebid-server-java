package org.prebid.server.spring.config;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.facebook.FacebookAdapter;
import org.prebid.server.bidder.index.IndexAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
public class BidderCatalogConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(BidderCatalogConfiguration.class);

    @Bean
    BidderCatalog bidderCatalog(List<BidderDeps> bidderDeps) {
        // There are no default values for some adapter properties. We don't want to force their presence in external
        // configuration and just skip adapters with incomplete configuration. But we want to make anyone deploying
        // the application aware of that nuisance.
        final List<Class<?>> adapterClasses = bidderDeps.stream()
                .map(BidderDeps::getAdapter)
                .map(Adapter::getClass)
                .collect(Collectors.toList());
        Stream.of(FacebookAdapter.class, IndexAdapter.class)
                .filter(clazz -> !adapterClasses.contains(clazz))
                .forEach(clazz -> logger.warn("{0} has not been initialized due to missing configuration properties. "
                        + "Please review configuration.", clazz.getSimpleName()));

        return new BidderCatalog(bidderDeps);
    }
}
