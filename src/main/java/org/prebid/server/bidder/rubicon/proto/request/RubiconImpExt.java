package org.prebid.server.bidder.rubicon.proto.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
public class RubiconImpExt {

    RubiconImpExtRp rp;

    List<String> viewabilityvendors;

    Integer maxbids;

    String gpid;

    ObjectNode skadn;

    String tid;

    RubiconImpExtPrebid prebid;
}
