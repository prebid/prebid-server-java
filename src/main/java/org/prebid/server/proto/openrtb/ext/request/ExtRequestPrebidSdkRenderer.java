package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

/**
 * Defines the contract for bidrequest.ext.prebid.sdk.renderers[i]
 */
@Value(staticConstructor = "of")
public class ExtRequestPrebidSdkRenderer {

    /**
     * Defines the contract for bidrequest.ext.prebid.sdk.renderers[i].name
     */
    String name;

    /**
     * Defines the contract for bidrequest.ext.prebid.sdk.renderers[i].version
     */
    String version;

    /**
     * Defines the contract for bidrequest.ext.prebid.sdk.renderers[i].url
     */
    String url;

    /**
     * Defines the contract for bidrequest.ext.prebid.sdk.renderers[i].data
     */
    ObjectNode data;

}
