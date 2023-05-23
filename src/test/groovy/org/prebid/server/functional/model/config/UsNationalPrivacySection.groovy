package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue

enum UsNationalPrivacySection {

    SHARING_NOTICE("SharingNotice"),
    SALE_OPT_OUT_NOTICE("SaleOptOutNotice"),
    SHARING_OPT_OUT_NOTICE("SharingOptOutNotice"),
    TARGETED_ADVERTISING_OPT_OUT_NOTICE("TargetedAdvertisingOptOutNotice"),
    SENSITIVE_DATA_PROCESSING_OPT_OUT_NOTICE("SensitiveDataProcessingOptOutNotice"),
    SENSITIVE_DATA_LIMIT_USE_NOTICE("SensitiveDataLimitUseNotice"),
    SALE_OPT_OUT("SaleOptOut"),
    SHARING_OPT_OUT("SharingOptOut"),
    TARGETED_ADVERTISING_OPT_OUT("TargetedAdvertisingOptOut"),
    SENSITIVE_DATA_PROCESSING("SensitiveDataProcessing"),
    KNOWN_CHILD_SENSITIVE_DATA_CONSENTS("KnownChildSensitiveDataConsents"),
    PERSONAL_DATA_CONSENTS("PersonalDataConsents"),
    MSPA_COVERED_TRANSACTION("MspaCoveredTransaction"),
    MSPA_OPT_OUT_OPTION_MODE("MspaOptOutOptionMode"),
    MSPA_SERVICE_PROVIDER_MODE("MspaServiceProviderMode"),
    SUBSECTION_TYPE("SubsectionType"),
    GPC("GPC");

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
