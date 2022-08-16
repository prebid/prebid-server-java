package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * This object represents the most general type of impression. Although the term
 * “banner” may have very specific meaning in other contexts, here it can be
 * many things including a simple static image, an expandable ad unit, or even
 * in-banner video (refer to the {@link Video} object in Section 3.2.7 for the
 * more generalized and full featured video ad units). An array of
 * {@link Banner} objects can also appear within the {@link Video} to describe
 * optional companion ads defined in the VAST specification.
 * <p>The presence of a {@link Banner} as a subordinate of the {@link Imp}
 * object indicates that this impression is offered as a banner type impression.
 * At the publisher’s discretion, that same impression may also be offered as
 * video, audio, and/or native by also including as {@link Imp} subordinates
 * objects of those types. However, any given bid for the impression must
 * conform to one of the offered types.
 */
@Builder(toBuilder = true)
@Value
public class Banner {

    /**
     * Array of {@link Format} objects (Section 3.2.10) representing the banner sizes
     * permitted. If none are specified, then use of the h and w attributes is
     * highly recommended.
     */
    List<Format> format;

    /**
     * Exact width in device independent pixels (DIPS);
     * recommended if no format objects are specified.
     */
    Integer w;

    /**
     * Exact height in device independent pixels (DIPS);
     * recommended if no format objects are specified.
     */
    Integer h;

    /**
     * Blocked banner ad types.
     * Values:
     * <p/> 1 = XHTML Text Ad,
     * <p/> 2 = XHTML Banner Ad,
     * <p/> 3 = JavaScript Ad,
     * <p/> 4 = iframe.
     */
    List<Integer> btype;

    /**
     * Blocked creative attributes. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--creative-attributes-">
     * List: Creative Attributes</a> in AdCOM 1.0.
     */
    List<Integer> battr;

    /**
     * Ad position on screen. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--placement-positions-">
     * List: Placement Positions</a> in AdCOM 1.0.
     */
    Integer pos;

    /**
     * Content MIME types supported. Popular MIME types may include, “image/jpeg” and “image/gif”.
     */
    List<String> mimes;

    /**
     * Indicates if the banner is in the top frame as opposed to an iframe,
     * where 0 = no, 1 = yes.
     */
    Integer topframe;

    /**
     * Directions in which the banner may expand. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--expandable-directions-">
     * List: Expandable Directions</a> in AdCOM 1.0.
     */
    List<Integer> expdir;

    /**
     * List of supported API frameworks for this impression. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--api-frameworks-">
     * List: API Frameworks</a>. If an API is not explicitly listed, it is assumed not to be supported.
     */
    List<Integer> api;

    /**
     * Unique identifier for this banner object. Recommended when {@link Banner} objects
     * are used with a {@link Video} object (Section 3.2.7) to represent an array of
     * companion ads. Values usually start at 1 and increase with each object;
     * should be unique within an impression.
     */
    String id;

    /**
     * Relevant only for {@link Banner} objects used with a {@link Video} object (Section 3.2.7)
     * in an array of companion ads. Indicates the companion banner rendering
     * mode relative to the associated video, where 0 = concurrent,
     * 1 = end-card.
     */
    Integer vcm;

    /**
     * Placeholder for exchange-specific extensions to OpenRTB.
     */
    ObjectNode ext;
}
