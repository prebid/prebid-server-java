package com.iab.openrtb.request;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;

/**
 * This object contains any legal, governmental, or industry regulations
 * that apply to the request. See Section 7.5 for more details on the
 * flags supporting Coppa, GDPR and CCPA.
 */
@Builder(toBuilder = true)
@Value
public class Regs {

    /**
     * Flag indicating if this request is subject to the COPPA regulations
     * established by the USA FTC, where 0 = no, 1 = yes. Refer to Section 7.5
     * for more information.
     */
    Integer coppa;

    /**
     * Flag that indicates whether or not the request is subject to
     * GDPR regulations 0 = No, 1 = Yes, omission indicates Unknown.
     * Refer to Section 7.5 for more information.
     */
    Integer gdpr;

    /**
     * Communicates signals regarding consumer privacy under US privacy regulation.
     * See <a href="https://github.com/InteractiveAdvertisingBureau/USPrivacy/blob/master/CCPA/US%20Privacy%20String.md">US Privacy String specifications</a>.
     * Refer to Section 7.5 for more information.
     */
    String usPrivacy;

    /**
     * Placeholder for exchange-specific extensions to OpenRTB.
     */
    ExtRegs ext;
}
