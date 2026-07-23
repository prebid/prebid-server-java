package org.prebid.server.bidder.magnite.proto.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Native;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class MagniteNative extends Native {

    ObjectNode requestNative;
}
