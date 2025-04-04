package org.prebid.server.hooks.modules.pb.response.correction.config;

import org.prebid.server.hooks.modules.pb.response.correction.core.ResponseCorrectionProvider;
import org.prebid.server.hooks.modules.pb.response.correction.core.correction.CorrectionProducer;
import org.prebid.server.hooks.modules.pb.response.correction.core.correction.appvideohtml.AppVideoHtmlCorrection;
import org.prebid.server.hooks.modules.pb.response.correction.core.correction.appvideohtml.AppVideoHtmlCorrectionProducer;
import org.prebid.server.hooks.modules.pb.response.correction.v1.ResponseCorrectionModule;
import org.prebid.server.json.ObjectMapperProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@ConditionalOnProperty(prefix = "hooks." + ResponseCorrectionModule.CODE, name = "enabled", havingValue = "true")
@Configuration
public class ResponseCorrectionModuleConfiguration {

    @Bean
    AppVideoHtmlCorrectionProducer appVideoHtmlCorrectionProducer(
            @Value("${logging.sampling-rate:0.01}") double logSamplingRate) {

        return new AppVideoHtmlCorrectionProducer(
                new AppVideoHtmlCorrection(ObjectMapperProvider.mapper(), logSamplingRate));
    }

    @Bean
    ResponseCorrectionProvider responseCorrectionProvider(List<CorrectionProducer> correctionProducers) {
        return new ResponseCorrectionProvider(correctionProducers);
    }

    @Bean
    ResponseCorrectionModule responseCorrectionModule(ResponseCorrectionProvider responseCorrectionProvider) {
        return new ResponseCorrectionModule(responseCorrectionProvider, ObjectMapperProvider.mapper());
    }
}
