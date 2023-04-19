package org.prebid.server.bidder.huaweiads.model.response;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class Monitor {

    String eventType;

    List<String> url;

}
