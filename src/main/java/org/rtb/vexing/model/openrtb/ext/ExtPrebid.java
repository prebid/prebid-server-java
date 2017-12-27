package org.rtb.vexing.model.openrtb.ext;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

/**
 * Defines the contract for any extension that has "prebid" and "bidder" fields
 */
@ToString
@EqualsAndHashCode
@AllArgsConstructor(staticName = "of")
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class ExtPrebid<P, B> {

    P prebid;

    B bidder;
}
