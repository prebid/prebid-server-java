package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.DsaTransparency;

import java.util.List;

/**
 * Defines the contract for bidresponse.seatbid[i].bid[i].ext.dsa
 */
@Value(staticConstructor = "of")
public class ExtBidDsa {

    /**
     * Defines the contract for bidresponse.seatbid[i].bid[i].ext.dsa.behalf
     */
    String behalf;

    /**
     * Defines the contract for bidresponse.seatbid[i].bid[i].ext.dsa.paid
     */
    String paid;

    /**
     * Defines the contract for bidresponse.seatbid[i].bid[i].ext.dsa.transparency[]
     */
    List<DsaTransparency> transparency;

    /**
     * Defines the contract for bidresponse.seatbid[i].bid[i].ext.dsa.adrender
     */
    @JsonProperty("adrender")
    Integer adRender;

}
