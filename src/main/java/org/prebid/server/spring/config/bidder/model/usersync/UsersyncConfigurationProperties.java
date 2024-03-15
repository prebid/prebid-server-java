package org.prebid.server.spring.config.bidder.model.usersync;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Data
@Validated
@NoArgsConstructor
public class UsersyncConfigurationProperties {

    Boolean enabled;

    @NotBlank
    String cookieFamilyName;

    UsersyncMethodConfigurationProperties redirect;

    UsersyncMethodConfigurationProperties iframe;
}
