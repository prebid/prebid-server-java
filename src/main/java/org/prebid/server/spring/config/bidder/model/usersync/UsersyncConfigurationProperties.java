package org.prebid.server.spring.config.bidder.model.usersync;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

@Data
@Validated
@NoArgsConstructor
public class UsersyncConfigurationProperties {

    @NotBlank
    String cookieFamilyName;

    UsersyncMethodConfigurationProperties redirect;

    UsersyncMethodConfigurationProperties iframe;
}
