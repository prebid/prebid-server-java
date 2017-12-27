package org.rtb.vexing.model.openrtb.ext.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

/**
 * Defines the contract for bidresponse.seatbid.bid[i].ext.prebid.cache
 */
@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class ExtResponseCache {

    String key;

    String url;
}
