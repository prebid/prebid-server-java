package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

/**
 * This object describes the network an ad will be displayed on.A
 * {@link Network} is defined as the parent entity of the {@link Channel}
 * object’s entity for the purposes of organizing Channels. Examples are
 * companies that own and/or license a collection of content channels
 * (Viacom, Discovery, CBS, WarnerMedia, Turner and others), or studio
 * that creates such content and self-distributes content. Name is a
 * human-readable field while domain and id can be used for reporting
 * and targeting purposes. See 7.6 for further examples.
 */
@Builder
@Value
public class Network {

    /**
     * A unique identifier assigned by the publisher. This may not be
     * a unique identifier across all supply sources.
     */
    String id;

    /**
     * Network the content is on (e.g., a TV network like “ABC")
     */
    String name;

    /**
     * The primary domain of the network (e.g. “abc.com” in the
     * case of thenetwork ABC). It is recommended to include the
     * top private domain (PSL+1) for DSP targeting normalization purposes.
     */
    String domain;

    /**
     * Placeholder for exchange-specific extensions to OpenRTB.
     */
    ObjectNode ext;
}
