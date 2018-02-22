package org.prebid.server.model.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.user.ext.digitrust
 * More info on DigiTrust can be found here: https://github.com/digi-trust/dt-cdn/wiki/Integration-Guide
 */
@AllArgsConstructor(staticName = "of")
@Value
public final class ExtUserDigiTrust {

    /**
     * Unique device identifier
     */
    String id;

    /**
     * Key version used to encrypt id
     */
    Integer keyv;

    /**
     * User optout preference
     */
    Integer pref;
}
