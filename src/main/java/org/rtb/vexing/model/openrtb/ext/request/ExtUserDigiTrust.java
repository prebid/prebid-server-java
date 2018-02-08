package org.rtb.vexing.model.openrtb.ext.request;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

/**
 * Defines the contract for bidrequest.user.ext.digitrust
 * More info on DigiTrust can be found here: https://github.com/digi-trust/dt-cdn/wiki/Integration-Guide
 */
@Builder
@ToString
@EqualsAndHashCode
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class ExtUserDigiTrust {

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
