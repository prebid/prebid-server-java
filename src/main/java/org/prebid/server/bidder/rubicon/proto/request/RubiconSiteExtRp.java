package org.prebid.server.bidder.rubicon.proto.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class RubiconSiteExtRp {

    Integer siteId;

    JsonNode target;
}
