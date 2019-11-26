package com.iab.openrtb.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * The Native Object is the top level JSON object which identifies a native response.
 * <p>
 * Note: Prior to VERSION 1.1, the native response’s root node was an object with a single
 * field “native” that would contain the object above as its value.
 * The Native Object specified above is now the root object.
 */
@Builder
@Value
public class Response {

    /**
     * Version of the Native Markup version in use. Default "1.2".
     */
    String ver;

    /**
     * List of native ad’s assets. Required if no assetsurl.
     */
    List<Asset> assets;

    /**
     * URL of an alternate source for the assets object.
     */
    String assetsurl;

    /**
     * URL where a dynamic creative specification may be found for populating this ad,
     * per the Dynamic Content Ads Specification.
     */
    String dcourl;

    /**
     * Destination Link. This is default link object for the ad.
     */
    Link link;

    /**
     * Array of impression tracking URLs, expected to return a 1x1 image or 204
     * response - typically only passed when using 3rd party trackers.
     * To be deprecated - replaced with eventtrackers
     */
    List<String> imptrackers;

    /**
     * Optional JavaScript impression tracker.
     */
    String jstracker;

    /**
     * Array of tracking objects to run with the ad, in response to the declared supported methods in the request.
     */
    List<EventTracker> eventtrackers;

    /**
     * If support was indicated in the request, URL of a page informing the user about the buyer’s
     * targeting activity.
     */
    String privacy;

    ObjectNode ext;
}
