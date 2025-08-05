package org.prebid.server.bidder.mediasquare.request;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class MediasquareBanner {

    List<List<Integer>> sizes;
}
