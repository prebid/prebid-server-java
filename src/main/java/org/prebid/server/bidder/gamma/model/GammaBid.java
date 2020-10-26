package org.prebid.server.bidder.gamma.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.response.Bid;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@Data
public class GammaBid extends Bid {

    @JsonProperty("vastXml")
    String vastXml;

    @JsonProperty("vastUrl")
    String vastUrl;
}
