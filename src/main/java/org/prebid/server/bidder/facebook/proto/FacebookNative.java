package org.prebid.server.bidder.facebook.proto;

import com.iab.openrtb.request.Native;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class FacebookNative extends Native {

    Integer w;

    Integer h;
}
