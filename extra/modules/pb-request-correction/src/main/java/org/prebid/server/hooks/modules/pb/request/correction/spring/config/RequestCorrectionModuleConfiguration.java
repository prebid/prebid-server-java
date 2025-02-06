package org.prebid.server.hooks.modules.pb.request.correction.spring.config;

import org.prebid.server.hooks.modules.pb.request.correction.core.RequestCorrectionProvider;
import org.prebid.server.hooks.modules.pb.request.correction.core.correction.CorrectionProducer;
import org.prebid.server.hooks.modules.pb.request.correction.core.correction.interstitial.InterstitialCorrectionProducer;
import org.prebid.server.hooks.modules.pb.request.correction.core.correction.useragent.UserAgentCorrectionProducer;
import org.prebid.server.hooks.modules.pb.request.correction.v1.RequestCorrectionModule;
import org.prebid.server.json.ObjectMapperProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConditionalOnProperty(prefix = "hooks." + RequestCorrectionModule.CODE, name = "enabled", havingValue = "true")
public class RequestCorrectionModuleConfiguration {

    @Bean
    InterstitialCorrectionProducer interstitialCorrectionProducer() {
        return new InterstitialCorrectionProducer();
    }

    @Bean
    UserAgentCorrectionProducer userAgentCorrectionProducer() {
        return new UserAgentCorrectionProducer();
    }

    @Bean
    RequestCorrectionProvider requestCorrectionProvider(List<CorrectionProducer> correctionProducers) {
        return new RequestCorrectionProvider(correctionProducers);
    }

    @Bean
    RequestCorrectionModule requestCorrectionModule(RequestCorrectionProvider requestCorrectionProvider) {
        return new RequestCorrectionModule(requestCorrectionProvider, ObjectMapperProvider.mapper());
    }
}
