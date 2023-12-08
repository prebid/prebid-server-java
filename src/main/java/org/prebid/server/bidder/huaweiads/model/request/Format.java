package org.prebid.server.bidder.huaweiads.model.request;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value(staticConstructor = "of")
@EqualsAndHashCode
public class Format {

    Integer w;

    Integer h;

}
