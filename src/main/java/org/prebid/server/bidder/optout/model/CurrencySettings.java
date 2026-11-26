package org.prebid.server.bidder.optout.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record CurrencySettings(String adServerCurrency, int granularityMultiplier) {

}
