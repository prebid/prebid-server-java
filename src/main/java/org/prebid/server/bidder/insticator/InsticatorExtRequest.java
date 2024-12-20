package org.prebid.server.bidder.insticator;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class InsticatorExtRequest {

    List<InsticatorExtRequestCaller> caller;

}
