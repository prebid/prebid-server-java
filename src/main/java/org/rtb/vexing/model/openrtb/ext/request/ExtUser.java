package org.rtb.vexing.model.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.user.ext
 */
@AllArgsConstructor(staticName = "of")
@Value
public final class ExtUser {

    ExtUserDigiTrust digitrust;
}
