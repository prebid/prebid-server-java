package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * This object defines the producer of the content in which the ad will be
 * shown. This is particularly useful when the content is syndicated and may be
 * distributed through different publishers and thus when the producer and
 * publisher are not necessarily the same entity.
 */
@Builder(toBuilder = true)
@Value
public class Producer {

    /**
     * Content producer or originator ID. Useful if content is syndicated and
     * may be posted on a site using embed tags.
     */
    String id;

    /**
     * Content producer or originator name (e.g., “Warner Bros”).
     */
    String name;

    /**
     * The taxonomy in use. Refer to the AdCOM list <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list_categorytaxonomies">List: Category
     * Taxonomies</a> for values.
     */
    Integer cattax;

    /**
     * Array of IAB content categories that describe the content producer.
     * The taxonomy to be used is defined by the cattax field. If no cattax
     * field is supplied IAB Content Category Taxonomy 1.0 is assumed.
     */
    List<String> cat;

    /**
     * Highest level domain of the content producer (e.g., “producer.com”).
     */
    String domain;

    /**
     * Placeholder for exchange-specific extensions to OpenRTB.
     */
    ObjectNode ext;
}
