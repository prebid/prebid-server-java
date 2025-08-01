package org.prebid.server.bidder.mediasquare.response;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class MediasquareResponse {

    List<MediasquareBid> responses;
}
