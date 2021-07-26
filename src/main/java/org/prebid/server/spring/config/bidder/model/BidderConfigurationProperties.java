package org.prebid.server.spring.config.bidder.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;

@Data
@Validated
@EqualsAndHashCode(callSuper = true)
public class BidderConfigurationProperties extends CommonBidderConfigurationProperties {

    @NotBlank
    private String endpoint;

    @NotNull
    private MetaInfo metaInfo;

    @NotNull
    private UsersyncConfigurationProperties usersync;

    private Map<String, String> extraInfo;

    private final Class<? extends BidderConfigurationProperties> selfClass;

    public BidderConfigurationProperties() {
        selfClass = this.getClass();
    }
}
