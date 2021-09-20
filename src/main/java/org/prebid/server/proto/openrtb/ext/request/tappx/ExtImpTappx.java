package org.prebid.server.proto.openrtb.ext.request.tappx;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpTappx {

    String host;

    String tappxkey;

    String endpoint;

    BigDecimal bidfloor;

    String mktag;

    List<String> bcid;

    List<String> bcrid;
}

