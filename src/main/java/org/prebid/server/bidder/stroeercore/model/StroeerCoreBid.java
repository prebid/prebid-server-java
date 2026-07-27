package org.prebid.server.bidder.stroeercore.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class StroeerCoreBid {

    String id;

    @JsonProperty("bidId")
    String bidId;

    BigDecimal cpm;

    Integer width;

    Integer height;

    @JsonProperty("ad")
    String adMarkup;

    @JsonProperty("crid")
    String creativeId;

    /**
     * @deprecated The dsa will move to the bid's ext.
     */
    @Deprecated(forRemoval = true)
    ObjectNode dsa;

    String mtype;

    List<String> adomain;

    ObjectNode ext;
}
