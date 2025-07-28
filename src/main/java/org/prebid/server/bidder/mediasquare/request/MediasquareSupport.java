package org.prebid.server.bidder.mediasquare.request;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Device;
import lombok.Value;

@Value(staticConstructor = "of")
public class MediasquareSupport {

    Device device;

    App app;
}
