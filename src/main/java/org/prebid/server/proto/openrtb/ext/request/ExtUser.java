package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.user.ext
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtUser {

    ExtUserPrebid prebid;


    /**
     * Consent is a GDPR consent string. See "Advised Extensions" of
     * https://iabtechlab.com/wp-content/uploads/2018/02/OpenRTB_Advisory_GDPR_2018-02.pdf
     */
    String consent;

    /**
     * DigiTrust breaks the typical Prebid Server convention of namespacing "global" options inside "ext.prebid.*"
     * to match the recommendation from the broader digitrust community.
     * <p>
     * For more info, see: https://github.com/digi-trust/dt-cdn/wiki/OpenRTB-extension#openrtb-2x
     */
    ExtUserDigiTrust digitrust;

    /**
     * A list of Universal IDs that contain PubCommon ID and Unified ID.
     */
    List<ExtUserTpId> tpid;
}
