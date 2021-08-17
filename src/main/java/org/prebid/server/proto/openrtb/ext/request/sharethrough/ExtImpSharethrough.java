package org.prebid.server.proto.openrtb.ext.request.sharethrough;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * Defines the contract for bidRequest.imp[i].ext.sharethrough
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpSharethrough {

    String pkey;

    Boolean iframe;

    @JsonProperty("iframeSize")
    List<Integer> iframeSize;

    BigDecimal bidfloor;

    ExtData data;
}
