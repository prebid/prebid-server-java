package org.prebid.server.privacy.ccpa;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;

@Value
@AllArgsConstructor(staticName = "of")
public class Ccpa {

    private static final char ENFORCED_SIGNAL = 'Y';
    private static final char NOT_ENFORCED_SIGNAL = 'N';
    private static final char NOT_DEFINED_SIGNAL = '-';
    private static final int VERSION_INDEX = 0;
    private static final int EXPLICIT_NOTICE_INDEX = 1;
    private static final int OPT_OUT_SALE_INDEX = 2;
    private static final int SERVICE_PROVIDER_AGREEMENT_INDEX = 3;
    private static final int LENGTH = 4;

    private String usPrivacy;

    public static final Ccpa EMPTY = Ccpa.of("");

    public boolean isCCPAEnforced() {
        try {
            validateUsPrivacy(usPrivacy);
        } catch (PreBidException ex) {
            return false;
        }
        return StringUtils.isNotBlank(usPrivacy) && usPrivacy.charAt(OPT_OUT_SALE_INDEX) == ENFORCED_SIGNAL;
    }

    public static void validateUsPrivacy(String usPrivacy) {
        if (StringUtils.isBlank(usPrivacy)) {
            return;
        }

        if (usPrivacy.length() != LENGTH) {
            throw new PreBidException("us_privacy must contain 4 characters");
        }

        if (usPrivacy.charAt(VERSION_INDEX) != '1') {
            throw new PreBidException("us_privacy must specify version 1");
        }
        agreementSpecified(usPrivacy.charAt(EXPLICIT_NOTICE_INDEX), "explicit notice");
        agreementSpecified(usPrivacy.charAt(OPT_OUT_SALE_INDEX), "opt-out sale");
        agreementSpecified(usPrivacy.charAt(SERVICE_PROVIDER_AGREEMENT_INDEX), "limited service provider agreement");
    }

    private static void agreementSpecified(char agreement, String agreementType) {
        if (agreement != NOT_ENFORCED_SIGNAL && agreement != ENFORCED_SIGNAL && agreement != NOT_DEFINED_SIGNAL) {
            throw new PreBidException(
                    String.format("us_privacy must specify 'N', 'Y', or '-' for the %s", agreementType));
        }
    }
}
