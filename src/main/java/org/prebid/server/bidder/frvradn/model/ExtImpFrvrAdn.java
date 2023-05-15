package org.prebid.server.bidder.frvradn.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpFrvrAdn {

    String publisherId;

    String adUnitId;
}
