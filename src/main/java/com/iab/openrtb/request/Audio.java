package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * This object represents an audio type impression. Many of the fields are
 * non-essential for minimally viable transactions, but are included to offer
 * fine control when needed. {@link Audio} in OpenRTB generally assumes compliance with
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
@Builder(toBuilder = true)
@Value
public class Audio {

    /**
     * Content MIME types supported (e.g., “audio/mp4”). <p/> (required)
     */
    List<String> mimes;

    /**
     * Minimum audio ad duration in seconds. This field is mutually exclusive
     * with rqddurs; only one of minduration and rqddurs may be in a bid request.
     */
    Integer minduration;

    /**
     * Maximum audio ad duration in seconds. This field is mutually exclusive
     * with rqddurs; only one of maxduration and rqddurs may be in a bid request.
     */
    Integer maxduration;

    /**
     * Indicates the total amount of time that advertisers may fill for a
     * “dynamic” audio ad pod, or the dynamic portion of a “hybrid”
     * ad pod. This field is required only for the dynamic portion(s) of
     * audio ad pods. This field refers to the length of the entire ad
     * break, whereas minduration/maxduration/rqddurs are
     * constraints relating to the slots that make up the pod.
     */
    Integer poddur;

    /**
     * Array of supported audio protocols. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--creative-subtypes---audiovideo-">
     * List: Creative Subtypes - Audio/Video</a> in AdCOM 1.0.
     */
    List<Integer> protocols;

    /**
     * Indicates the start delay in seconds for pre-roll, mid-roll, or post-roll
     * ad placements. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--start-delay-modes-">
     * List: Start Delay Modes</a> in AdCOM 1.0.
     */
    Integer startdelay;

    /**
     * Precise acceptable durations for audio creatives in seconds. This
     * field specifically targets the live audio/radio use case where
     * non-exact ad durations would result in undesirable ‘dead air’.
     * This field is mutually exclusive with minduration and
     * maxduration; if rqddurs is specified, minduration and
     * maxduration must not be specified and vice versa.
     */
    List<Integer> rqddurs;

    /**
     * Unique identifier indicating that an impression opportunity
     * belongs to an audioad pod. If multiple impression opportunities
     * within a bid request share the same podid, this indicates that
     * those impression opportunities belong to the same audio ad pod.
     */
    Integer podid;

    /**
     * The sequence (position) of the audio ad pod within a
     * content stream. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list_podsequence">List: Pod Sequence</a> in AdCOM 1.0
     * for guidance on the use of this field.
     */
    Integer podseq;

    /**
     * If multiple ad impressions are offered in the same bid request, the
     * sequence number will allow for the coordinated delivery of multiple
     * creatives.
     */
    Integer sequence;

    /**
     * For audio ad pods, this value indicates that the seller can
     * guarantee delivery against the indicated sequence. Refer to
     * <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--pod-sequence-">List: Slot Position</a> in Pod in AdCOM 1.0 for guidance on the
     * use of this field.
     */
    Integer slotinpod;

    /**
     * Minimum CPM per second. This is a price floor for the
     * “dynamic” portion of an audio ad pod, relative to the duration
     * of bids an advertiser may submit.
     */
    BigDecimal mincpmpersec;

    /**
     * Blocked creative attributes. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--creative-attributes-">
     * List: Creative Attributes</a> in AdCOM 1.0.
     */
    List<Integer> battr;

    /**
     * Maximum extended ad duration if extension is allowed. If blank or 0,
     * extension is not allowed. If -1, extension is allowed, and there is no
     * time limit imposed. If greater than 0, then the value represents the
     * number of seconds of extended play supported beyond the maxduration
     * value.
     */
    Integer maxextended;

    /**
     * Minimum bit rate in Kbps.
     */
    Integer minbitrate;

    /**
     * Maximum bit rate in Kbps.
     */
    Integer maxbitrate;

    /**
     * Supported delivery methods (e.g., streaming, progressive). If none
     * specified, assume all are supported. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--delivery-methods-">
     * List: Delivery Methods</a> in AdCOM 1.0.
     */
    List<Integer> delivery;

    /**
     * Array of {@link Banner} objects (Section 3.2.6) if companion ads are available.
     */
    List<Banner> companionad;

    /**
     * List of supported API frameworks for this impression. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--api-frameworks-">
     * List: API Frameworks</a> in AdCOM 1.0. If an API is not explicitly listed,
     * it is assumed not to be supported.
     */
    List<Integer> api;

    /**
     * Supported DAAST companion ad types. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--companion-types-">
     * List: Companion Types</a> in AdCOM 1.0. Recommended if
     * companion {@link Banner} objects are included via the companionad array.
     */
    List<Integer> companiontype;

    /**
     * The maximum number of ads that can be played in an ad pod.
     */
    Integer maxseq;

    /**
     * Type of audio feed. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--feed-types-">
     * List: Feed Types</a> in AdCOM 1.0.
     */
    Integer feed;

    /**
     * Indicates if the ad is stitched with audio content or delivered
     * independently, where 0 = no, 1 = yes.
     */
    Integer stitched;

    /**
     * Volume normalization mode. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--volume-normalization-modes-">
     * List: Volume Normalization Modes</a> in AdCOM 1.0.
     */
    Integer nvol;

    /**
     * Placeholder for exchange-specific extensions to OpenRTB.
     */
    ObjectNode ext;
}
