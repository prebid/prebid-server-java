package org.prebid.server.proto.openrtb.ext.request.aduptech;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAduptech {

    String publisher;

    String placement;

    String query;

    Boolean adtest;

    Boolean debug;

    ObjectNode ext;
}

