package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.regs.ext.dsa
 */
@Value(staticConstructor = "of")
public class ExtRegsDsa {

    /**
     * Defines the contract for bidrequest.regs.ext.dsa.dsarequired
     */
    @JsonProperty("dsarequired")
    Integer dsaRequired;

    /**
     * Defines the contract for bidrequest.regs.ext.dsa.pubrender
     */
    @JsonProperty("pubrender")
    Integer pubRender;

    /**
     * Defines the contract for bidrequest.regs.ext.dsa.datatopub
     */
    @JsonProperty("datatopub")
    Integer dataToPub;

    /**
     * Defines the contract for bidrequest.regs.ext.dsa.transparency[]
     */
    List<ExtRegsDsaTransparency> transparency;

}
