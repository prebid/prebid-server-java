package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.regs.ext.dsa.transparency[i]
 */
@Value(staticConstructor = "of")
public class ExtRegsDsaTransparency {

    /**
     * Defines the contract for bidrequest.regs.ext.dsa.transparency[i].domain
     */
    String domain;

    /**
     * Defines the contract for bidrequest.regs.ext.dsa.transparency[i].params[]
     */
    List<Integer> params;
}
