package org.prebid.server.deals.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;

@AllArgsConstructor(staticName = "of")
@Value
public class Price {

    BigDecimal cpm;

    String currency;
}
