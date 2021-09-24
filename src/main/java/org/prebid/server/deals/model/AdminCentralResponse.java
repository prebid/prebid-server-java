package org.prebid.server.deals.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class AdminCentralResponse {

    LogTracer tracer;

    @JsonProperty("storedrequest")
    Command storedRequest;

    @JsonProperty("storedrequest-amp")
    Command storedRequestAmp;

    @JsonProperty("line-items")
    Command lineItems;

    Command account;

    ServicesCommand services;
}
