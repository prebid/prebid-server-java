package org.prebid.server.spring.config.bidder.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Validated
@Data
@NoArgsConstructor
public class BidderConfigurationProperties {

    @NotNull
    private Boolean enabled;

    @NotBlank
    private String endpoint;

    @NotNull
    private Boolean pbsEnforcesGdpr;

    @NotNull
    private List<String> deprecatedNames;

    @NotNull
    private List<String> aliases;

    @NotNull
    private MetaInfo metaInfo;

    @NotNull
    private UsersyncConfigurationProperties usersync;
}
