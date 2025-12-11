package org.prebid.server.hooks.modules.id5.userid.v1.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.hooks.modules.id5.userid.v1.Id5IdModule;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.DecimalMax;

/**
 * Configuration model for the ID5 ID module.
 */
@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "hooks." + Id5IdModule.CODE)
@Validated
public class Id5IdModuleProperties {

    private Long partner;

    @NotBlank
    private String providerName;

    private String inserterName;

    @NotBlank
    @Pattern(regexp = "https?://.+", message = "must be a valid http(s) URL")
    private String fetchEndpoint = "https://api.id5-sync.com/gs/v2";

    @PositiveOrZero
    @DecimalMax(value = "1.0")
    private double fetchSamplingRate;

    private ValuesFilter<String> bidderFilter;
    private ValuesFilter<String> accountFilter;
    private ValuesFilter<String> countryFilter;
}
