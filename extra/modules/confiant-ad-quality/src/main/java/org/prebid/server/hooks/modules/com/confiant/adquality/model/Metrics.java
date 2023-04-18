package org.prebid.server.hooks.modules.com.confiant.adquality.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.HashMap;

/**
 * The Confiant RTB API provides event metrics to help monitor the service availability and performance,
 * most importantly for new creatives. As bids get submitted to the RTB API, a first timestamp is immediately stored
 * to represent the time of submission (submitted event). New creatives are queued and fetched asynchronously
 * by the Confiant application (fetched event). Creatives are scanned to assess creative attributes and potential issues
 * (scanned event). Finally, results are synchronized back to the Redis instance (synchronized event).
 * All DateTime strings are represented in ISO 8601 format.
 */
@Data
public class Metrics {

     /** Represents the first time the creative was submitted via the RTB API. Can be null for legacy creative keys. */
     String submitted;

     /** Represents the first time the creative was fetched by Confiant’s application. */
     String fetched;

     /** Represents the last time the creative was scanned. Can be null if creative was not scanned yet for your account. */
     String scanned;

     /**
      * Object with two elements:
      * - first: ISO 8601 DateTime string (or null for legacy creative keys) representing the first full synchronization back to the Redis instance
      * - last: ISO 8601 DateTime string representing the time of last full synchronization back to the Redis instance
      */
     @JsonProperty("synchronized")
     HashMap<String, String> synchronizedData;
}
