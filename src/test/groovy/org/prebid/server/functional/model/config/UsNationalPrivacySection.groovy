package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue
import com.iab.gpp.encoder.field.UspNatV1Field
import org.prebid.server.functional.util.PBSUtils

enum UsNationalPrivacySection {

    SHARING_NOTICE(UspNatV1Field.SHARING_NOTICE),
    SALE_OPT_OUT_NOTICE(UspNatV1Field.SALE_OPT_OUT_NOTICE),
    SHARING_OPT_OUT_NOTICE(UspNatV1Field.SHARING_OPT_OUT_NOTICE),
    TARGETED_ADVERTISING_OPT_OUT_NOTICE(UspNatV1Field.TARGETED_ADVERTISING_OPT_OUT_NOTICE),
    SENSITIVE_DATA_PROCESSING_OPT_OUT_NOTICE(UspNatV1Field.SENSITIVE_DATA_PROCESSING_OPT_OUT_NOTICE),
    SENSITIVE_DATA_LIMIT_USE_NOTICE(UspNatV1Field.SENSITIVE_DATA_LIMIT_USE_NOTICE),
    SALE_OPT_OUT(UspNatV1Field.SALE_OPT_OUT),
    SHARING_OPT_OUT(UspNatV1Field.SHARING_OPT_OUT),
    TARGETED_ADVERTISING_OPT_OUT(UspNatV1Field.TARGETED_ADVERTISING_OPT_OUT),
    SENSITIVE_DATA_RACIAL_RANDOM(UspNatV1Field.SENSITIVE_DATA_PROCESSING + PBSUtils.getRandomNumber(1, 12)),
    SENSITIVE_DATA_PROCESSING_ALL(UspNatV1Field.SENSITIVE_DATA_PROCESSING + "*"),
    CHILD_CONSENTS_FROM_RANDOM(UspNatV1Field.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS + PBSUtils.getRandomNumber(1, 2)),
    PERSONAL_DATA_CONSENTS(UspNatV1Field.PERSONAL_DATA_CONSENTS),
    MSPA_COVERED_TRANSACTION(UspNatV1Field.MSPA_COVERED_TRANSACTION),
    MSPA_OPT_OUT_OPTION_MODE(UspNatV1Field.MSPA_OPT_OUT_OPTION_MODE),
    MSPA_SERVICE_PROVIDER_MODE(UspNatV1Field.MSPA_SERVICE_PROVIDER_MODE),
    GPC(UspNatV1Field.GPC.toUpperCase());

    @JsonValue
    final String value

    private UsNationalPrivacySection(String value) {
        this.value = value
    }

    static UsNationalPrivacySection valueFromText(String value) {
        values().find { section -> section.value.equalsIgnoreCase(value) }
                ?: { throw new IllegalArgumentException("Invalid UsNationalPrivacySection value: $value") }()
    }
}
