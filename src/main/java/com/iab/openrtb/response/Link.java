package com.iab.openrtb.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * Used for ‘call to action’ assets, or other links from the Native ad.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class Link {

    /**
     * Landing URL of the clickable link.
     */
    String url;

    /**
     * List of third-party tracker URLs to be fired on click of the URL.
     */
    List<String> clicktrackers;

    /**
     * Fallback URL for deeplink. To be used if the URL given in url is not supported by the device.
     */
    String fallback;

    ObjectNode ext;
}
