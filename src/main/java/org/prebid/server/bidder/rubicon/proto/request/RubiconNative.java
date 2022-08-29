package org.prebid.server.bidder.rubicon.proto.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Native;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RubiconNative extends Native {

    ObjectNode requestNative;
}
