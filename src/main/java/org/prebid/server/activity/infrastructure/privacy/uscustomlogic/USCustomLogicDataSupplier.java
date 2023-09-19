package org.prebid.server.activity.infrastructure.privacy.uscustomlogic;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
        final DataAggregator dataAggregator = new DataAggregator();

        dataAggregator.put("Version", gppReader.getVersion());

        dataAggregator.put("Gpc", gppReader.getGpc());
        dataAggregator.put("GpcSegmentType", gppReader.getGpcSegmentType());
        dataAggregator.put("GpcSegmentIncluded", gppReader.getGpcSegmentIncluded());

        dataAggregator.put("SaleOptOut", gppReader.getSaleOptOut());
        dataAggregator.put("SaleOptOutNotice", gppReader.getSaleOptOutNotice());

        dataAggregator.put("SharingNotice", gppReader.getSharingNotice());
        dataAggregator.put("SharingOptOut", gppReader.getSharingOptOut());
        dataAggregator.put("SharingOptOutNotice", gppReader.getSharingOptOutNotice());

        dataAggregator.put("TargetedAdvertisingOptOut", gppReader.getTargetedAdvertisingOptOut());
        dataAggregator.put("TargetedAdvertisingOptOutNotice", gppReader.getTargetedAdvertisingOptOutNotice());

        dataAggregator.put("SensitiveDataLimitUseNotice", gppReader.getSensitiveDataLimitUseNotice());
        dataAggregator.put("SensitiveDataProcessing", gppReader.getSensitiveDataProcessing());
        dataAggregator.put("SensitiveDataProcessingOptOutNotice", gppReader.getSensitiveDataProcessingOptOutNotice());

        dataAggregator.put("KnownChildSensitiveDataConsents", gppReader.getKnownChildSensitiveDataConsents());

        dataAggregator.put("PersonalDataConsents", gppReader.getPersonalDataConsents());

        dataAggregator.put("MspaCoveredTransaction", gppReader.getMspaCoveredTransaction());
        dataAggregator.put("MspaServiceProviderMode", gppReader.getMspaServiceProviderMode());
        dataAggregator.put("MspaOptOutOptionMode", gppReader.getMspaOptOutOptionMode());

        return dataAggregator.data();
    }

    private static class DataAggregator {

        private final Map<String, Object> data = new HashMap<>();

        public void put(String key, Object value) {
            if (value instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    data.put(key + i, list.get(i));
                }
            } else {
                data.put(key, value);
            }
        }

        public Map<String, Object> data() {
            return Collections.unmodifiableMap(data);
        }
    }
}
