package org.prebid.server.bidder.criteo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEid;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class CriteoRequest {

    String id;

    CriteoPublisher publisher;

    CriteoUser user;

    @JsonProperty("gdprconsent")
    CriteoGdprConsent gdprConsent;

    List<CriteoRequestSlot> slots;

    List<ExtUserEid> eids;
}
