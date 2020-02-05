package org.prebid.server.proto.openrtb.ext.request.tappx;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;

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
}

