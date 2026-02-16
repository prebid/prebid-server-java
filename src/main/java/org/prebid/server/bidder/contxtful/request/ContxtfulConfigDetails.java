package org.prebid.server.bidder.contxtful.request;

import lombok.Value;

@Value(staticConstructor = "of")
public class ContxtfulConfigDetails {

    String version;

    String customer;
}
