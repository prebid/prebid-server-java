package com.iab.openrtb.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * This object describes the content in which the impression will appear, which
 * may be syndicated or non- syndicated content. This object may be useful when
 * syndicated content contains impressions and does not necessarily match the
 * publisher’s general content. The exchange might or might not have knowledge
 * of the page where the content is running, because of the syndication
 * method. For example, might be a video impression embedded in an iframe on an
 * unknown web property or device.
 */
@Builder(toBuilder = true)
@Value
public class Content {

    private static final Content EMPTY = Content.builder().build();

    /**
     * ID uniquely identifying the content.
     */
    String id;

    /**
     * Episode number.
     */
    Integer episode;

    /**
     * Content title.
     * <p>Video Examples: “Search Committee” (television), “A New Hope” (movie),
     * or “Endgame” (made for web).
     * <p>Non-Video Example: “Why an Antarctic Glacier Is Melting So Quickly”
     * (Time magazine article).
     */
    String title;

    /**
     * Content series.
     * <p>Video Examples: “The Office” (television), “Star Wars” (movie),
     * or “Arby ‘N’ The Chief” (made for web).
     * <p>Non-Video Example: “Ecocentric” (Time Magazine blog).
     */
    String series;

    /**
     * Content season (e.g., “Season 3”).
     */
    String season;

    /**
     * Artist credited with the content.
     */
    String artist;

    /**
     * Genre that best describes the content (e.g., rock, pop, etc).
     */
    String genre;

    /**
     * Album to which the content belongs; typically for audio.
     */
    String album;

    /**
     * International Standard Recording Code conforming to ISO- 3901.
     */
    String isrc;

    /**
     * Details about the content {@link Producer} (Section 3.2.17).
     */
    Producer producer;

    /**
     * URL of the content, for buy-side contextualization or review.
     */
    String url;

    /**
     * The taxonomy in use. Refer to the AdCOM list <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list_categorytaxonomies">List: Category
     * Taxonomies</a> for values.
     */
    Integer cattax;

    /**
     * Array of IAB content categories that describe the content.
     * The taxonomy to be used is defined by the cattax field.If no cattax
     * field is supplied IAB Content Category Taxonomy 1.0 is assumed.
     */
    List<String> cat;

    /**
     * Production quality. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--production-qualities-">
     * List: Production Qualities</a> in AdCOM 1.0 .
     */
    Integer prodq;

    /**
     * Type of content (game, video, text, etc.). Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--content-contexts-">
     * List: Content Contexts</a> in AdCOM 1.0.
     */
    Integer context;

    /**
     * Content rating (e.g., MPAA).
     */
    String contentrating;

    /**
     * User rating of the content (e.g., number of stars, likes, etc.).
     */
    String userrating;

    /**
     * Media rating per IQG guidelines. Refer to <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--media-ratings-">
     * List: Media Ratings</a> in AdCOM 1.0.
     */
    Integer qagmediarating;

    /**
     * Comma separated list of keywords describing the content. Only one of ‘keywords’ or ‘kwarray’ may be present.
     */
    String keywords;

    /**
     * Array of keywords about the site. Only one of ‘keywords’ or ‘kwarray’ may be present.
     */
    List<String> kwarray;

    /**
     * 0 = not live, 1 = content is live (e.g., stream, live blog).
     */
    Integer livestream;

    /**
     * 0 = indirect, 1 = direct.
     */
    Integer sourcerelationship;

    /**
     * Length of content in seconds; appropriate for video or audio.
     */
    Integer len;

    /**
     * Content language using ISO-639-1-alpha-2. Only one of language or langb should be present.
     */
    String language;

    /**
     * Content language using IETF BCP 47. Only one of language or langb should be present.
     */
    String langb;

    /**
     * Indicator of whether or not the content is embeddable (e.g., an
     * embeddable video player), where 0 = no, 1 = yes.
     */
    Integer embeddable;

    /**
     * Additional content data. Each {@link Data} object (Section 3.2.21) represents a
     * different data source.
     */
    List<Data> data;

    /**
     * Details about the {@link Network} (Section 3.2.23) the content is on.
     */
    Network network;

    /**
     * Details about the {@link Channel} (Section 3.2.24) the content is on.
     */
    Channel channel;

    /**
     * Placeholder for exchange-specific extensions to OpenRTB.
     */
    ObjectNode ext;

    @JsonIgnore
    public boolean isEmpty() {
        return this.equals(EMPTY);
    }
}
