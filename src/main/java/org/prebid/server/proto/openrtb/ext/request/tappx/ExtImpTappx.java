package org.prebid.server.proto.openrtb.ext.request.tappx;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpTappx {

    String host;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String tappxkey;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String endpoint;

    BigDecimal bidfloor;

    String mktag;

    List<String> bcid;

    List<String> bcrid;
}

