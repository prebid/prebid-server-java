package org.prebid.server.activity.infrastructure.privacy.uscustomlogic;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class USCustomLogicDataSupplier implements Supplier<Map<String, Object>> {

    private final USCustomLogicGppReader gppReader;

    private USCustomLogicDataSupplier(USCustomLogicGppReader gppReader) {
        this.gppReader = Objects.requireNonNull(gppReader);
    }

    public static USCustomLogicDataSupplier of(USCustomLogicGppReader gppReader) {
        return new USCustomLogicDataSupplier(gppReader);
    }

    @Override
    public Map<String, Object> get() {
        final Map<String, Object> data = new HashMap<>();

        data.put("Version", gppReader.getVersion());

        data.put("Gpc", gppReader.getGpc());
        data.put("GpcSegmentType", gppReader.getGpcSegmentType());
        data.put("GpcSegmentIncluded", gppReader.getGpcSegmentIncluded());

        data.put("SaleOptOut", gppReader.getSaleOptOut());
        data.put("SaleOptOutNotice", gppReader.getSaleOptOutNotice());

        data.put("SharingNotice", gppReader.getSharingNotice());
        data.put("SharingOptOut", gppReader.getSharingOptOut());
        data.put("SharingOptOutNotice", gppReader.getSharingOptOutNotice());

        data.put("TargetedAdvertisingOptOut", gppReader.getTargetedAdvertisingOptOut());
        data.put("TargetedAdvertisingOptOutNotice", gppReader.getTargetedAdvertisingOptOutNotice());

        data.put("SensitiveDataLimitUseNotice", gppReader.getSensitiveDataLimitUseNotice());
        data.put("SensitiveDataProcessing", gppReader.getSensitiveDataProcessing());
        data.put("SensitiveDataProcessingOptOutNotice", gppReader.getSensitiveDataProcessingOptOutNotice());

        data.put("KnownChildSensitiveDataConsents", gppReader.getKnownChildSensitiveDataConsents());

        data.put("PersonalDataConsents", gppReader.getPersonalDataConsents());

        data.put("MspaCoveredTransaction", gppReader.getMspaCoveredTransaction());
        data.put("MspaServiceProviderMode", gppReader.getMspaServiceProviderMode());
        data.put("MspaOptOutOptionMode", gppReader.getMspaOptOutOptionMode());

        return data;
    }
}
