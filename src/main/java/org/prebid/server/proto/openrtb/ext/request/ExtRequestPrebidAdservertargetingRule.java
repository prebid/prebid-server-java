package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

/**
 * Defines the contract for bidrequest.ext.prebid.adservertargeting
 */
@Value
public class ExtRequestPrebidAdservertargetingRule {

    String key;

    String source;

    String value;
}
