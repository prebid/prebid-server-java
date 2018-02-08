package org.rtb.vexing.model.openrtb.ext.request;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

/**
 * Defines the contract for bidrequest.user.ext
 */
@Builder(toBuilder = true)
@ToString
@EqualsAndHashCode
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class ExtUser {

    ExtUserDigiTrust digitrust;
}
