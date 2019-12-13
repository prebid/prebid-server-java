package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * ExtRegs defines the contract for bidrequest.regs.ext
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtRegs {

    /**
     * GDPR should be "1" if the caller believes the user is subject to GDPR laws, "0" if not, and undefined
     * if it's unknown. For more info on this parameter, see:
     * https://iabtechlab.com/wp-content/uploads/2018/02/OpenRTB_Advisory_GDPR_2018-02.pdf
     */
    Integer gdpr;

    /**
     * CCPA determined that U.S. Privacy applies to the transaction
     * For more info on this parameter, see:
     * https://iabtechlab.com/wp-content/uploads/2019/10/CCPA_Compliance_Framework_OpenRTB_Extension_Proposal_US_Privacy_IABTechLab_DRAFT_for_Public_Comment.pdf
     */
    String usPrivacy;
}
