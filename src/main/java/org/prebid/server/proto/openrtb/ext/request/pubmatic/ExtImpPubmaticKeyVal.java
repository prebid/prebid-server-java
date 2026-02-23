package org.prebid.server.proto.openrtb.ext.request.pubmatic;

import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.imp[i].ext.pubmatic.keywords[i]
 */
@Value(staticConstructor = "of")
public class ExtImpPubmaticKeyVal {

    String key;

    List<String> value;
}
