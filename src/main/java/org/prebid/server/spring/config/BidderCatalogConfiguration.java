package org.prebid.server.spring.config;

import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderDeps;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class BidderCatalogConfiguration {

    @Bean
    BidderCatalog bidderCatalog(List<BidderDeps> bidderDeps) {
        return new BidderCatalog(bidderDeps);
    }
}
