package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * This object represents an audio type impression. Many of the fields are
 * non-essential for minimally viable transactions, but are included to offer
 * fine control when needed. Audio in OpenRTB generally assumes compliance with
 * the DAAST standard. As such, the notion of companion ads is supported by
 * optionally including an array of {@link Banner} objects (refer to the
 * {@link Banner} object in Section 3.2.6) that define these companion ads.
 * <p>The presence of a {@link Audio} as a subordinate of the {@link Imp} object
 * indicates that this impression is offered as an audio type impression. At the
 * publisher’s discretion, that same impression may also be offered as banner,
 * video, and/or native by also including as {@link Imp} subordinates objects of
 * those types. However, any given bid for the impression must conform to one of
 * the offered types.
 */
@Builder
@Value
public class Audio {

    /** Content MIME types supported (e.g., “audio/mp4”). (required) */
    List<String> mimes;

    /** Minimum audio ad duration in seconds. (recommended) */
    Integer minduration;

    /** Maximum audio ad duration in seconds. (recommended) */
    Integer maxduration;

    /** Array of supported audio protocols. Refer to List 5.8. (recommended) */
    List<Integer> protocols;

    /**
     * Indicates the start delay in seconds for pre-roll, mid-roll, or post-roll
     * ad placements. Refer to List 5.12.
     * (recommended)
     */
    Integer startdelay;

    /**
     * If multiple ad impressions are offered in the same bid request, the
     * sequence number will allow for the coordinated delivery of multiple
     * creatives.
     */
    Integer sequence;

    /** Blocked creative attributes. Refer to List 5.3. */
    List<Integer> battr;

    /**
     * Maximum extended ad duration if extension is allowed. If blank or 0,
     * extension is not allowed. If -1, extension is allowed, and there is no
     * time limit imposed. If greater than 0, then the value represents the
     * number of seconds of extended play supported beyond the maxduration
     * value.
     */
    Integer maxextended;

    /** Minimum bit rate in Kbps. */
    Integer minbitrate;

    /** Maximum bit rate in Kbps. */
    Integer maxbitrate;

    /**
     * Supported delivery methods (e.g., streaming, progressive). If none
     * specified, assume all are supported. Refer to List 5.15.
     */
    List<Integer> delivery;

    /**
     * Array of Banner objects (Section 3.2.6) if companion ads are available.
     */
    List<Banner> companionad;

    /**
     * List of supported API frameworks for this impression. Refer to List 5.6.
     * If an API is not explicitly listed, it is assumed not to be supported.
     */
    List<Integer> api;

    /**
     * Supported DAAST companion ad types. Refer to List 5.14. Recommended if
     * companion Banner objects are included via the companionad array.
     */
    List<Integer> companiontype;

    /** The maximum number of ads that can be played in an ad pod. */
    Integer maxseq;

    /** Type of audio feed. Refer to List 5.16. */
    Integer feed;

    /**
     * Indicates if the ad is stitched with audio content or delivered
     * independently, where 0 = no, 1 = yes.
     */
    Integer stitched;

    /** Volume normalization mode. Refer to List 5.17. */
    Integer nvol;

    /** Placeholder for exchange-specific extensions to OpenRTB. */
    ObjectNode ext;
}
