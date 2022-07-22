package org.prebid.server.spring.config.bidder.model.usersync;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.UsersyncFormat;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Validated
@NoArgsConstructor
public class UsersyncMethodConfigurationProperties {

    @NotBlank
    String url;

    String uidMacro;

    @NotNull
    Boolean supportCors;

    UsersyncFormat formatOverride;
}
