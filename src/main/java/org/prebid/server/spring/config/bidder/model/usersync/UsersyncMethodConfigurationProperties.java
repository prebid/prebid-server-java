package org.prebid.server.spring.config.bidder.model.usersync;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
public class UsersyncMethodConfigurationProperties {

    String url;

    String uidMacro;

    @NotNull
    Boolean supportCors;
}
