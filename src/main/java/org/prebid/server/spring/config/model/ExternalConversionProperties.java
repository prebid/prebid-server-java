package org.prebid.server.spring.config.model;

import io.vertx.core.Vertx;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;

@Validated
@Data
@AllArgsConstructor
public class ExternalConversionProperties {

    @NotBlank
    String currencyServerUrl;

    @NotNull
    @Min(2)
    Long defaultTimeoutMs;

    @NotNull
    @Min(2)
    Long refreshPeriodMs;

    @NotNull
    Long staleAfterMs;

    @Min(2)
    Long stalePeriodMs;

    @NotNull
    Vertx vertx;

    @NotNull
    HttpClient httpClient;

    @NotNull
    Metrics metrics;

    @NotNull
    Clock clock;

    @NotNull
    JacksonMapper mapper;
}

