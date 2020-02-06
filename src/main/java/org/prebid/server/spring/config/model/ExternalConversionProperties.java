package org.prebid.server.spring.config.model;

import io.vertx.core.Vertx;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Validated
@Data
@AllArgsConstructor
public class ExternalConversionProperties {

    @NotBlank
    String currencyServerUrl;

    @NotNull
    @Min(2)
    Long defaultTimeout;

    @NotNull
    @Min(2)
    Long refreshPeriod;

    @NotNull
    Vertx vertx;

    @NotNull
    HttpClient httpClient;

    @NotNull
    JacksonMapper mapper;
}

