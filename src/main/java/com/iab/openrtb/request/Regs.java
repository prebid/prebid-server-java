package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * This object contains any legal, governmental, or industry regulations that
 * apply to the request. The {@code coppa} flag signals whether or not the
 * request falls under the United States Federal Trade Commission’s regulations
 * for the United States Children’s Online Privacy Protection Act (“COPPA”).
 */
@AllArgsConstructor(staticName = "of")
@Value
public class Regs {

    /**
     * Flag indicating if this request is subject to the COPPA regulations
     * established by the USA FTC, where 0 = no, 1 = yes. Refer to Section 7.5
     * for more information.
     */
    Integer coppa;

    /** Placeholder for exchange-specific extensions to OpenRTB. */
    ObjectNode ext;
}
