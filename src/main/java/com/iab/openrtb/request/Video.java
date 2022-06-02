package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * This object represents an in-stream video impression. Many of the fields are
 * non-essential for minimally viable transactions, but are included to offer
 * fine control when needed. {@link Video} in OpenRTB generally assumes compliance with
 * the VAST standard. As such, the notion of companion ads is supported by
 * optionally including an array of {@link Banner} objects (refer to the
 * {@link Banner} object in Section 3.2.6) that define these companion ads.
 * <p>The presence of a {@link Video} as a subordinate of the {@link Imp} object
 * indicates that this impression is offered as a video type impression. At the
 * publisher’s discretion, that same impression may also be offered as banner,
 * audio, and/or native by also including as {@link Imp} subordinates objects of
 * those types. However, any given bid for the impression must conform to one of
 * the offered types.
 */
@Builder(toBuilder = true)
@Value
public class Video {

    /**
     * Content MIME types supported (e.g., “video/mp4”).
     * <p/> (required)
     */
    List<String> mimes;

    /**
     * Minimum video ad duration in seconds. This field is mutually exclusive
     * with rqddurs; only one of minduration and rqddurs may be in a bid request.
     */
    Integer minduration;

    /**
     * Maximum video ad duration in seconds. This field is mutually exclusive
     * with rqddurs; only one of maxduration and rqddurs may be in a bid request.
     */
    Integer maxduration;

    /**
     * Indicates the start delay in seconds for pre-roll, mid-roll, or post-roll
     * ad placements. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--start-delay-modes-">
     * List: Start Delay Modes</a> in AdCOM 1.0.
     */
    Integer startdelay;

    /**
     * Indicates the maximum number of ads that may be served into
     * a “dynamic” video ad pod (where the precise number of ads is
     * not predetermined by the seller). See Section 7.6 for more details.
     */
    Integer maxseq;

    /**
     * Indicates the total amount of time in seconds that advertisers
     * may fill for a “dynamic” video ad pod (See Section 7.6 for more
     * details), or the dynamic portion of a “hybrid” ad pod. This field
     * is required only for the dynamic portion(s) of video ad pods.
     * This field refers to the length of the entire ad break, whereas
     * minduration/maxduration/rqddurs are constraints relating to
     * the slots that make up the pod.
     */
    Integer poddur;

    /**
     * Array of supported video protocols. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--creative-subtypes---audiovideo-">
     * List: Creative Subtypes - Audio/Video</a> in AdCOM 1.0.
     */
    List<Integer> protocols;

    /**
     * Width of the video player in device independent pixels (DIPS).
     */
    Integer w;

    /**
     * Height of the video player in device independent pixels (DIPS).
     */
    Integer h;

    /**
     * Unique identifier indicating that an impression opportunity
     * belongs to a video ad pod. If multiple impression opportunities
     * within a bid request share the same podid, this indicates that
     * those impression opportunities belong to the same video ad pod.
     */
    Integer podid;

    /**
     * The sequence (position) of the video ad pod within a
     * content stream. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list_podsequence">List: Pod Sequence</a> in AdCOM 1.0
     * for guidance on the use of this field.
     */
    Integer podseq;

    /**
     * Precise acceptable durations for video creatives in
     * seconds. This field specifically targets the Live TV use case
     * where non-exact ad durations would result in undesirable
     * ‘dead air’. This field is mutually exclusive with minduration
     * and maxduration; if rqddurs is specified, minduration and
     * maxduration must not be specified and vice versa.
     */
    List<Integer> rqddurs;

    /**
     * Video placement type for the impression. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--placement-subtypes---video-">
     * List: Placement Subtypes - Video</a> in AdCOM 1.0.
     */
    Integer placement;

    /**
     * Indicates if the impression must be linear, nonlinear, etc. If none
     * specified, assume all are allowed. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--linearity-modes-">
     * List: Linearity Modes</a> in AdCOM 1.0. Note that this field describes
     * the expected VAST response and not whether a placement is in-stream,
     * out-stream, etc. For that, see placement.
     */
    Integer linearity;

    /**
     * Indicates if the player will allow the video to be skipped, where 0 = no,
     * 1 = yes. <p>If a bidder sends markup/creative that is itself skippable,
     * the Bid object should include the attr array with an element of 16
     * indicating skippable video. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--creative-attributes-">
     * List: Creative Attributes</a> in AdCOM 1.0.
     */
    Integer skip;

    /**
     * Videos of total duration greater than this number of seconds can be
     * skippable; only applicable if the ad is skippable.
     */
    Integer skipmin;

    /**
     * Number of seconds a video must play before skipping is enabled; only
     * applicable if the ad is skippable.
     */
    Integer skipafter;

    /**
     * If multiple ad impressions are offered in the same bid request, the
     * sequence number will allow for the coordinated delivery of multiple
     * creatives.
     */
    Integer sequence;

    /**
     * For video ad pods, this value indicates that the seller can
     * guarantee delivery against the indicated slot position in the
     * pod. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--slot-position-in-pod-">List: Slot Position in Pod</a> in AdCOM 1.0 guidance
     * on the use of this field.
     */
    Integer slotinpod;

    /**
     * Minimum CPM per second. This is a price floor for the
     * “dynamic” portion of a video ad pod, relative to the duration
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
     * Indicates if letter-boxing of 4:3 content into a 16:9 window is allowed,
     * where 0 = no, 1 = yes.
     */
    Integer boxingallowed;

    /**
     * Playback methods that may be in use. If none are specified, any method
     * may be used. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--playback-methods-">
     * List: Playback Methods</a> in AdCOM 1.0. Only one method is typically used in
     * practice. As a result, this array may be converted to an Integer in a
     * future version of the specification. It is strongly advised to use only
     * the first element of this array in preparation for this change.
     */
    List<Integer> playbackmethod;

    /**
     * The event that causes playback to end. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--playback-cessation-modes-">
     * List: Playback Cessation Modes</a> in AdCOM 1.0.
     */
    Integer playbackend;

    /**
     * Supported delivery methods (e.g., streaming, progressive). If none
     * specified, assume all are supported. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--delivery-methods-">
     * List: Delivery Methods</a> in AdCOM 1.0.
     */
    List<Integer> delivery;

    /**
     * Ad position on screen. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--placement-positions-">
     * List: Placement Positions</a> in AdCOM 1.0.
     */
    Integer pos;

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
     * Supported VAST companion ad types. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--companion-types-">
     * List: Companion Types</a> in AdCOM 1.0. Recommended if companion {@link Banner}
     * objects are included via the companion ad array. If one of these banners will
     * be rendered as an end-card, this can be specified using the vcm attribute
     * with the particular banner (Section 3.2.6).
     */
    List<Integer> companiontype;

    /**
     * Placeholder for exchange-specific extensions to OpenRTB.
     */
    ObjectNode ext;
}
