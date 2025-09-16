package org.prebid.server.bidder.gamma.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.iab.openrtb.response.Bid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@Builder
@AllArgsConstructor(onConstructor = @__(@JsonIgnore))
@RequiredArgsConstructor
public class GammaBid {

    @JsonUnwrapped
    Bid bid;

    @JsonProperty("vastXml")
    String vastXml;

    @JsonProperty("vastUrl")
    String vastUrl;

}
