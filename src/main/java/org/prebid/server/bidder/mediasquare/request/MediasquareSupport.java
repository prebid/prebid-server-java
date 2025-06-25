package org.prebid.server.bidder.mediasquare.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class MediasquareSupport {

    ObjectNode device;

    ObjectNode app;
}
