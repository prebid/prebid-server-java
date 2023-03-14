package org.prebid.server.auction.gpp.model.privacy;

import lombok.Value;

@Value(staticConstructor = "of")
public class TcfEuV2Privacy implements Privacy {

    Integer gdpr;

    String consent;
}
