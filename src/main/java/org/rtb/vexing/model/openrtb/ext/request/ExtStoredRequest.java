package org.rtb.vexing.model.openrtb.ext.request;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

/**
 * Defines the contract for ext.prebid.storedrequest
 */
@ToString
@EqualsAndHashCode
@AllArgsConstructor(staticName = "of")
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class ExtStoredRequest {

    /**
     * Defines the contract for ext.prebid.storedrequest.id
     */
    String id;
}
