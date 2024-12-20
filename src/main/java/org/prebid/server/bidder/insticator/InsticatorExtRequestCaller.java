package org.prebid.server.bidder.insticator;

import lombok.Value;

@Value(staticConstructor = "of")
public class InsticatorExtRequestCaller {

    String name;

    String version;

}
