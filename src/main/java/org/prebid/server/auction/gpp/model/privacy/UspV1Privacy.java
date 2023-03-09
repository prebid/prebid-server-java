package org.prebid.server.auction.gpp.model.privacy;

import lombok.Value;

@Value(staticConstructor = "of")
public class UspV1Privacy implements Privacy {

    String usPrivacy;
}
