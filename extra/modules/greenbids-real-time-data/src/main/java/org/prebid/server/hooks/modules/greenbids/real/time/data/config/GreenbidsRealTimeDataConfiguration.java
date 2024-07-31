package org.prebid.server.hooks.modules.greenbids.real.time.data.config;

import ai.onnxruntime.OrtException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.GreenbidsRealTimeDataProperties;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.GreenbidsRealTimeDataModule;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.GreenbidsRealTimeDataProcessedAuctionRequestHook;
import org.prebid.server.json.ObjectMapperProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@ConditionalOnProperty(prefix = "hooks." + GreenbidsRealTimeDataModule.CODE, name = "enabled", havingValue = "true")
@Configuration
public class GreenbidsRealTimeDataConfiguration {

    @Bean
    GreenbidsRealTimeDataModule greenbidsRealTimeDataModule(
            @Value("${hooks.modules.greenbids-real-time-data.param1}") String param1,
            @Value("${hooks.modules.greenbids-real-time-data.param2}") Double param2) throws OrtException {
        final ObjectMapper mapper = ObjectMapperProvider.mapper();

        final GreenbidsRealTimeDataProperties globalProperties = GreenbidsRealTimeDataProperties.of(
                param1,
                param2
        );

        //OnnxModelRunner modelRunner = new OnnxModelRunner("extra/modules/greenbids-real-time-data/src/main/resources/onnx_rf_v2_pbs_user_agent.onnx");

        //System.out.println(
        //        "GreenbidsRealTimeDataConfiguration/greenbidsRealTimeDataModule" + "\n" +
        //                "params: " + param1 + " " + param2 + "\n" +
        //                "modelRunner: " + modelRunner + "\n"
        //);

        return new GreenbidsRealTimeDataModule(List.of(
                new GreenbidsRealTimeDataProcessedAuctionRequestHook(mapper)));
    }
}
