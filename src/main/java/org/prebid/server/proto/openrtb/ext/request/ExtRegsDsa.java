package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.regs.ext.dsa
 */
@Value(staticConstructor = "of")
public class ExtRegsDsa {

    /**
     * Defines the contract for bidrequest.regs.ext.dsa.required
     */
    Integer required;

    /**
     * Defines the contract for bidrequest.regs.ext.dsa.pubrender
     */
    Integer pubrender;

    /**
     * Defines the contract for bidrequest.regs.ext.dsa.datatopub
     */
    Integer datatopub;

    /**
     * Defines the contract for bidrequest.regs.ext.dsa.transparency[]
     */
    List<ExtRegsDsaTransparency> transparency;

}
