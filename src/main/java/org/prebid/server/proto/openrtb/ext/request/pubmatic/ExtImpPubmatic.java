package org.prebid.server.proto.openrtb.ext.request.pubmatic;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.bidder.pubmatic.model.request.PubmaticWrapper;

import java.util.List;

/**
 * Defines the contract for bidrequest.imp[i].ext.pubmatic
 * PublisherId and adSlot are mandatory parameters, others are optional parameters
 * Keywords is bid specific parameter,
 * WrapExt needs to be sent once per bid request
 */
@Builder
@Value
public class ExtImpPubmatic {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("adSlot")
    String adSlot;

    String dctr;

    @JsonProperty("pmzoneid")
    String pmZoneId;

    PubmaticWrapper wrapper;

    List<ExtImpPubmaticKeyVal> keywords;

    String kadfloor;
}
