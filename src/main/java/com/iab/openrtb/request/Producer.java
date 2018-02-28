package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

import java.util.List;

/**
 * This object defines the producer of the content in which the ad will be
 * shown. This is particularly useful when the content is syndicated and may be
 * distributed through different publishers and thus when the producer and
 * publisher are not necessarily the same entity.
 */
@Value
public class Producer {

    /**
     * Content producer or originator ID. Useful if content is syndicated and
     * may be posted on a site using embed tags.
     */
    String id;

    /** Content producer or originator name (e.g., “Warner Bros”). */
    String name;

    /**
     * Array of IAB content categories that describe the content producer.
     * Refer to List 5.1.
     */
    List<String> cat;

    /** Highest level domain of the content producer (e.g., “producer.com”). */
    String domain;

    /** Placeholder for exchange-specific extensions to OpenRTB. */
    ObjectNode ext;
}
