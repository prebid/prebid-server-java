package org.prebid.server.bidder.mediasquare.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;

import java.util.List;

@Builder
@Value(staticConstructor = "of")
public class MediasquareRequest {

    List<MediasquareCode> codes;

    MediasquareGdpr gdpr;

    String type;

    ExtRegsDsa dsa;

    @JsonProperty("tech")
    MediasquareSupport support;

    Boolean test;
}
