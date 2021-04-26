package org.prebid.server.spring.config.bidder.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Validated
@Data
@NoArgsConstructor
public class UsersyncConfigurationProperties {

    String url;

    String redirectUrl;

    @NotBlank
    String cookieFamilyName;

    @NotBlank
    String type;

    @NotNull
    Boolean supportCors;

    SecondaryConfigurationProperties secondary;

    @Validated
    @Data
    @NoArgsConstructor
    public static class SecondaryConfigurationProperties {

        String url;

        String redirectUrl;

        @NotBlank
        String type;

        @NotNull
        Boolean supportCors;
    }
}
