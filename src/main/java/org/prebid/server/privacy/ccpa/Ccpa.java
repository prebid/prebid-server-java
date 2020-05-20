package org.prebid.server.privacy.ccpa;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;

@Value
@AllArgsConstructor(staticName = "of")
public class Ccpa {

    public static final Ccpa EMPTY = Ccpa.of("");
    private static final String CONSENT_FORMAT_REGEX = "1([yY\\-nN]){3}";
    private static final String ENFORCED_SIGNAL = "Y";
    private static final String NOT_ENFORCED_SIGNAL = "N";
    private static final String NOT_DEFINED_SIGNAL = "-";
    private static final int VERSION_INDEX = 0;
    private static final int EXPLICIT_NOTICE_INDEX = 1;
    private static final int OPT_OUT_SALE_INDEX = 2;
    private static final int SERVICE_PROVIDER_AGREEMENT_INDEX = 3;
    private static final int LENGTH = 4;
    private String usPrivacy;

    public boolean isNotEmpty() {
        return StringUtils.isNotEmpty(usPrivacy);
    }

    public boolean isCCPAEnforced() {
        try {
            validateUsPrivacy(usPrivacy);
        } catch (PreBidException ex) {
            return false;
        }

        final String optOutSale = StringUtils.isNotBlank(usPrivacy)
                ? Character.toString(usPrivacy.charAt(OPT_OUT_SALE_INDEX))
                : null;
        return StringUtils.equalsIgnoreCase(optOutSale, ENFORCED_SIGNAL);
    }

    public static boolean isCcpaString(String consentString) {
        return StringUtils.isNotBlank(consentString) && consentString.matches(CONSENT_FORMAT_REGEX);
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
        final boolean isAppropriateValue = StringUtils.equalsAnyIgnoreCase(Character.toString(agreement),
                NOT_ENFORCED_SIGNAL, ENFORCED_SIGNAL, NOT_DEFINED_SIGNAL);

        if (!isAppropriateValue) {
            throw new PreBidException(
                    String.format("us_privacy must specify 'N' or 'n', 'Y' or 'y', '-' for the %s", agreementType));
        }
    }
}
