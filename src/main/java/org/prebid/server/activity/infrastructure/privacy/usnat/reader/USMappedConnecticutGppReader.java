package org.prebid.server.activity.infrastructure.privacy.usnat.reader;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.section.UsCtV1;
import org.prebid.server.activity.infrastructure.privacy.uscustomlogic.USCustomLogicGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.USNatGppReader;
import org.prebid.server.util.ObjectUtil;

import java.util.List;
import java.util.Objects;

public class USMappedConnecticutGppReader implements USNatGppReader, USCustomLogicGppReader {

    private static final List<Integer> NON_CHILD_SENSITIVE_DATA = List.of(0, 0);
    private static final List<Integer> MIXED_CHILD_SENSITIVE_DATA = List.of(2, 1);
    private static final List<Integer> CHILD_SENSITIVE_DATA = List.of(1, 1);

    private final UsCtV1 consent;

    public USMappedConnecticutGppReader(GppModel gppModel) {
        consent = gppModel != null ? gppModel.getUsCtV1Section() : null;
    }

    @Override
    public Integer getVersion() {
        return ObjectUtil.getIfNotNull(consent, UsCtV1::getVersion);
    }

    @Override
    public Boolean getGpc() {
        return ObjectUtil.getIfNotNull(consent, UsCtV1::getGpc);
    }

    @Override
    public Boolean getGpcSegmentType() {
        return null;
    }

    @Override
    public Boolean getGpcSegmentIncluded() {
        return ObjectUtil.getIfNotNull(consent, UsCtV1::getGpcSegmentIncluded);
    }

    @Override
    public Integer getSaleOptOut() {
        return ObjectUtil.getIfNotNull(consent, UsCtV1::getSaleOptOut);
    }

    @Override
    public Integer getSaleOptOutNotice() {
        return ObjectUtil.getIfNotNull(consent, UsCtV1::getSaleOptOutNotice);
    }

    @Override
    public Integer getSharingNotice() {
        return ObjectUtil.getIfNotNull(consent, UsCtV1::getSharingNotice);
    }

    @Override
    public Integer getSharingOptOut() {
        return null;
    }

    @Override
    public Integer getSharingOptOutNotice() {
        return null;
    }

    @Override
    public Integer getTargetedAdvertisingOptOut() {
        return ObjectUtil.getIfNotNull(consent, UsCtV1::getTargetedAdvertisingOptOut);
    }

    @Override
    public Integer getTargetedAdvertisingOptOutNotice() {
        return ObjectUtil.getIfNotNull(consent, UsCtV1::getTargetedAdvertisingOptOutNotice);
    }

    @Override
    public Integer getSensitiveDataLimitUseNotice() {
        return null;
    }

    @Override
    public List<Integer> getSensitiveDataProcessing() {
        return ObjectUtil.getIfNotNull(consent, UsCtV1::getSensitiveDataProcessing);
    }

    @Override
    public Integer getSensitiveDataProcessingOptOutNotice() {
        return null;
    }

    @Override
    public List<Integer> getKnownChildSensitiveDataConsents() {
        final List<Integer> originalData = ObjectUtil.getIfNotNull(
                consent, UsCtV1::getKnownChildSensitiveDataConsents);

        final Integer first = originalData != null ? originalData.get(0) : null;
        final Integer second = originalData != null ? originalData.get(1) : null;
        final Integer third = originalData != null ? originalData.get(2) : null;

        if (Objects.equals(first, 0) && Objects.equals(second, 0) && Objects.equals(third, 0)) {
            return NON_CHILD_SENSITIVE_DATA;
        }

        return Objects.equals(second, 2) && Objects.equals(third, 2)
                ? MIXED_CHILD_SENSITIVE_DATA
                : CHILD_SENSITIVE_DATA;
    }

    @Override
    public Integer getPersonalDataConsents() {
        return null;
    }

    @Override
    public Integer getMspaCoveredTransaction() {
        return ObjectUtil.getIfNotNull(consent, UsCtV1::getMspaCoveredTransaction);
    }

    @Override
    public Integer getMspaServiceProviderMode() {
        return ObjectUtil.getIfNotNull(consent, UsCtV1::getMspaServiceProviderMode);
    }

    @Override
    public Integer getMspaOptOutOptionMode() {
        return ObjectUtil.getIfNotNull(consent, UsCtV1::getMspaOptOutOptionMode);
    }
}
