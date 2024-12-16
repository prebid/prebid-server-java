package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExtIgiIgs {

    String impid;

    ObjectNode config;

    ExtIgiIgsExt ext;
}
