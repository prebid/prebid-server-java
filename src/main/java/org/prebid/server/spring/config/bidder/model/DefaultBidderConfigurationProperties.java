package org.prebid.server.spring.config.bidder.model;

import lombok.Data;
import org.prebid.server.auction.versionconverter.OrtbVersion;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Validated
@Data
public class DefaultBidderConfigurationProperties {

    @NotNull
    private Boolean enabled;

    @NotNull
    private OrtbVersion ortbVersion;

    @NotNull
    private Boolean pbsEnforcesCcpa;

    @NotNull
    private Boolean modifyingVastXmlAllowed;

    @NotNull
    private Debug debug;

    private final Map<String, Object> aliases = Collections.emptyMap();

    private final List<String> deprecatedNames = Collections.emptyList();

    private final Map<String, String> extraInfo = Collections.emptyMap();

    private CompressionType endpointCompression = CompressionType.NONE;
}
