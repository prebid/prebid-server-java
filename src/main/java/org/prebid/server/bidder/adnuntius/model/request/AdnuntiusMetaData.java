package org.prebid.server.bidder.adnuntius.model.request;

import lombok.Value;

@Value(staticConstructor = "of")
public class AdnuntiusMetaData {

    String usi;
}
