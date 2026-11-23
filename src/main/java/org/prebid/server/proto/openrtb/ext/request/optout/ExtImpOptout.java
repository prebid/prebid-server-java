package org.prebid.server.proto.openrtb.ext.request.optout;

import lombok.Value;

import java.util.Map;

@Value(staticConstructor = "of")
public class ExtImpOptout {

    String publisher;

    String adSlot;

    Map<String, String> customs;

    Boolean includeOrtb2;
}
