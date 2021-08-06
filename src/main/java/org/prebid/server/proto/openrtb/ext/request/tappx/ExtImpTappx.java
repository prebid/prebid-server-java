package org.prebid.server.proto.openrtb.ext.request.tappx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * Defines the contract for bidRequest.imp[i].ext.tappx
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpTappx {

    String host;

    String tappxkey;

    String endpoint;

    BigDecimal bidfloor;

    @JsonProperty("mktag")
    String mkTag;

    List<String> bcid;

    List<String> bcrid;
}

