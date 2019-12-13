package com.iab.openrtb.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Builder
@Value
public class EventTracker {

    /**
     * Type of event to track.
     */
    Integer event;

    /**
     * Type of tracking requested.
     */
    Integer method;

    /**
     * The URL of the image or js. Required for image or js, optional for custom.
     */
    String url;

    /**
     * To be agreed individually with the exchange, an array of key:value objects for custom tracking.
     */
    Map<String, String> customdata;

    ObjectNode ext;
}
