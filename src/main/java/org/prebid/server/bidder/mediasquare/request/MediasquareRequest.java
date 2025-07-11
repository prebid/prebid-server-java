package org.prebid.server.bidder.mediasquare.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value(staticConstructor = "of")
public class MediasquareRequest {

    List<MediasquareCode> codes;

    MediasquareGdpr gdpr;

    String type;

    ObjectNode dsa;

    @JsonProperty("tech")
    MediasquareSupport support;

    Boolean test;
}
