package org.prebid.server.proto.openrtb.ext.response;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtIgiIgsExt {

    String bidder;

    String adapter;
}
