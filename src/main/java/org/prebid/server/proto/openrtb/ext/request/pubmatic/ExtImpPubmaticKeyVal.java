package org.prebid.server.proto.openrtb.ext.request.pubmatic;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.imp[i].ext.pubmatic.keywords[i]
 */
@Builder
@Value
public class ExtImpPubmaticKeyVal {

    String key;

    List<String> values;
}
