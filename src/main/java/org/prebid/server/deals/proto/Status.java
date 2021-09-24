package org.prebid.server.deals.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.deals.proto.report.DeliveryProgressReport;

@AllArgsConstructor(staticName = "of")
@Value
public class Status {

    @JsonProperty("currencyRates")
    CurrencyServiceState currencyRates;

    @JsonProperty("dealsStatus")
    DeliveryProgressReport deliveryProgressReport;

}
