package org.prebid.server.bidder.mediasquare.request;

import com.iab.openrtb.request.Video;
import lombok.Builder;
import lombok.Value;

@Builder
@Value(staticConstructor = "of")
public class MediasquareMediaTypes {

    MediasquareBanner banner;

    Video video;

    String nativeRequest;
}
