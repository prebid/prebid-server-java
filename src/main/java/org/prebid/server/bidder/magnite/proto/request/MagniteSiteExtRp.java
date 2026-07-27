package org.prebid.server.bidder.magnite.proto.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class MagniteSiteExtRp {

    Integer siteId;

    JsonNode target;
}
