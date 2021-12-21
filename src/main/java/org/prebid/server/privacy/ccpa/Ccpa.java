package org.prebid.server.privacy.ccpa;

import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;

@Value(staticConstructor = "of")
public class Ccpa {

    public static final Ccpa EMPTY = Ccpa.of(null);

    private static final int LENGTH = 4;
    private static final int VERSION_INDEX = 0;
    private static final int EXPLICIT_NOTICE_INDEX = 1;
    private static final int OPT_OUT_SALE_INDEX = 2;
    private static final int SERVICE_PROVIDER_AGREEMENT_INDEX = 3;

    private static final String ENFORCED_SIGNAL = "Y";
    private static final String NOT_ENFORCED_SIGNAL = "N";
    private static final String NOT_DEFINED_SIGNAL = "-";

    String usPrivacy;

    public boolean isNotEmpty() {
        return StringUtils.isNotEmpty(usPrivacy);
    }

    public boolean isEnforced() {
        try {
            validateUsPrivacy(usPrivacy);
        } catch (PreBidException e) {
            return false;
        }

        final String optOutSale = Character.toString(usPrivacy.charAt(OPT_OUT_SALE_INDEX));
        return StringUtils.equalsIgnoreCase(optOutSale, ENFORCED_SIGNAL);
    }

    public static boolean isValid(String consent) {
        try {
            validateUsPrivacy(consent);
            return true;
        } catch (PreBidException e) {
            return false;
        }
    }

    public static void validateUsPrivacy(String consent) {
        if (StringUtils.length(consent) != LENGTH) {
            throw new PreBidException("us_privacy must contain 4 characters");
        }

        if (consent.charAt(VERSION_INDEX) != '1') {
            throw new PreBidException("us_privacy must specify version 1");
        }

        agreementSpecified(consent.charAt(EXPLICIT_NOTICE_INDEX), "explicit notice");
        agreementSpecified(consent.charAt(OPT_OUT_SALE_INDEX), "opt-out sale");
        agreementSpecified(consent.charAt(SERVICE_PROVIDER_AGREEMENT_INDEX), "limited service provider agreement");
    }

    private static void agreementSpecified(char agreement, String agreementType) {
        final boolean isAppropriateValue = StringUtils.equalsAnyIgnoreCase(Character.toString(agreement),
                NOT_ENFORCED_SIGNAL, ENFORCED_SIGNAL, NOT_DEFINED_SIGNAL);

        if (!isAppropriateValue) {
            throw new PreBidException(
                    String.format("us_privacy must specify 'N' or 'n', 'Y' or 'y', '-' for the %s", agreementType));
        }
    }
}
