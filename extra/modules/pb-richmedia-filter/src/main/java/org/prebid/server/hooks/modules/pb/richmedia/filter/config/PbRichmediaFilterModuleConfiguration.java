package org.prebid.server.hooks.modules.pb.richmedia.filter.config;

import org.prebid.server.hooks.modules.pb.richmedia.filter.core.BidResponsesMraidFilter;
import org.prebid.server.hooks.modules.pb.richmedia.filter.v1.PbRichmediaFilterBidResponsesFilterHook;
import org.prebid.server.hooks.modules.pb.richmedia.filter.v1.PbRichmediaFilterModule;
import org.prebid.server.json.ObjectMapperProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@ConditionalOnProperty(prefix = "hooks." + PbRichmediaFilterModule.CODE, name = "enabled", havingValue = "true")
@Configuration
public class PbRichmediaFilterModuleConfiguration {

    @Bean
    PbRichmediaFilterModule pbRichmediaFilterModule(
            @Value("${hooks.modules.pb-richmedia-filter.filter-mraid}") Boolean filterMraid,
            @Value("${hooks.modules.pb-richmedia-filter.mraid-script-pattern}") String mraidScriptPattern) {

        return new PbRichmediaFilterModule(List.of(
                new PbRichmediaFilterBidResponsesFilterHook(
                        ObjectMapperProvider.mapper(),
                        new BidResponsesMraidFilter(mraidScriptPattern),
                        Boolean.TRUE.equals(filterMraid))));
    }

}
