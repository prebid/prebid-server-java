package org.prebid.server.bidder.sharethrough.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class StrUriParameters {

    String pkey;

    String bidID;

    String gpid;

    Boolean consentRequired;

    String consentString;

    String usPrivacySignal;

    Boolean instantPlayCapable;

    Boolean iframe;

    Integer height;

    Integer width;

    String theTradeDeskUserId;

    String sharethroughUserId;

    SharethroughRequestBody body;
}

