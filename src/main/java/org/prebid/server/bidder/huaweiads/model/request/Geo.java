package org.prebid.server.bidder.huaweiads.model.request;

import lombok.Value;

@Value(staticConstructor = "of")
public class Geo {

    Float lon;

    Float lat;

    Integer accuracy;

    Integer lastfix;

}
