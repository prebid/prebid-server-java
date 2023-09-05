package org.prebid.server.spring.config.bidder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.huaweiads.HuaweiAdSlotBuilder;
import org.prebid.server.bidder.huaweiads.HuaweiAdmBuilder;
import org.prebid.server.bidder.huaweiads.HuaweiAdsBidder;
import org.prebid.server.bidder.huaweiads.HuaweiAppBuilder;
import org.prebid.server.bidder.huaweiads.HuaweiDeviceBuilder;
import org.prebid.server.bidder.huaweiads.HuaweiNetworkBuilder;
import org.prebid.server.bidder.huaweiads.model.request.PkgNameConvert;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.config.bidder.util.UsersyncerCreator;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Configuration
@PropertySource(value = "classpath:/bidder-config/huaweiads.yaml", factory = YamlPropertySourceFactory.class)
public class HuaweiAdsConfiguration {

    private static final String BIDDER_NAME = "huaweiads";

    @Bean("huaweiadsConfigurationProperties")
    @ConfigurationProperties("adapters.huaweiads")
    HuaweiAdsConfigurationProperties configurationProperties() {
        return new HuaweiAdsConfigurationProperties();
    }

    @Bean
    BidderDeps huaweiAdsBidderDeps(HuaweiAdsConfigurationProperties huaweiadsConfigurationProperties,
                                   @NotBlank @Value("${external-url}") String externalUrl,
                                   JacksonMapper mapper) {

        return BidderDepsAssembler.<HuaweiAdsConfigurationProperties>forBidder(BIDDER_NAME)
                .withConfig(huaweiadsConfigurationProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> {
                    final ExtraInfo extraInfo = config.getExtraInfo();
                    return new HuaweiAdsBidder(
                            config.getEndpoint(),
                            extraInfo.getChineseEndpoint(),
                            extraInfo.getRussianEndpoint(),
                            extraInfo.getEuropeanEndpoint(),
                            extraInfo.getAsianEndpoint(),
                            extraInfo.getCloseSiteSelectionByCountry(),
                            mapper,
                            new HuaweiAdSlotBuilder(mapper),
                            new HuaweiAppBuilder(extraInfo.getPkgNameConvert()),
                            new HuaweiDeviceBuilder(mapper),
                            new HuaweiNetworkBuilder(),
                            new HuaweiAdmBuilder(mapper));
                })
                .assemble();
    }

    @Validated
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class HuaweiAdsConfigurationProperties extends BidderConfigurationProperties {

        @Valid
        @NotNull
        private ExtraInfo extraInfo = new ExtraInfo();
    }

    @Data
    @NoArgsConstructor
    private static class ExtraInfo {

        List<PkgNameConvert> pkgNameConvert;

        String closeSiteSelectionByCountry;

        String chineseEndpoint;

        String russianEndpoint;

        String europeanEndpoint;

        String asianEndpoint;
    }

}
