package org.prebid.server.bidder.rubicon.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class RubiconSiteExt {

    RubiconSiteExtRp rp;

    /**
     * AMP should be 1 if the request comes from an AMP page, 0 if not or be undefined.
     */
    Integer amp;
}
