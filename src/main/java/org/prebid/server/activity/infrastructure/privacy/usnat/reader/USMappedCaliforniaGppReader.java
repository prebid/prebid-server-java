package org.prebid.server.activity.infrastructure.privacy.usnat.reader;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.section.UsCa;
import org.prebid.server.activity.infrastructure.privacy.uscustomlogic.USCustomLogicGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.USNatGppReader;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class USMappedCaliforniaGppReader implements USNatGppReader, USCustomLogicGppReader {

    private static final List<Integer> DEFAULT_SENSITIVE_DATA = Collections.nCopies(12, null);
    private static final List<Integer> CHILD_SENSITIVE_DATA = List.of(1, 1);

    private final UsCa consent;

    public USMappedCaliforniaGppReader(GppModel gppModel) {
        consent = gppModel != null ? gppModel.getUsCaSection() : null;
    }

    @Override
    public Integer getVersion() {
        return ObjectUtil.getIfNotNull(consent, UsCa::getVersion);
    }

    @Override
    public Boolean getGpc() {
        return ObjectUtil.getIfNotNull(consent, UsCa::getGpc);
    }

    @Override
    public Boolean getGpcSegmentType() {
        return null;
    }

    @Override
    public Boolean getGpcSegmentIncluded() {
        return ObjectUtil.getIfNotNull(consent, UsCa::getGpcSegmentIncluded);
    }

    @Override
    public Integer getSaleOptOut() {
        return ObjectUtil.getIfNotNull(consent, UsCa::getSaleOptOut);
    }

    @Override
    public Integer getSaleOptOutNotice() {
        return ObjectUtil.getIfNotNull(consent, UsCa::getSaleOptOutNotice);
    }

    @Override
    public Integer getSharingNotice() {
        return null;
    }

    @Override
    public Integer getSharingOptOut() {
        return ObjectUtil.getIfNotNull(consent, UsCa::getSharingOptOut);
    }

    @Override
    public Integer getSharingOptOutNotice() {
        return ObjectUtil.getIfNotNull(consent, UsCa::getSharingOptOutNotice);
    }

    @Override
    public Integer getTargetedAdvertisingOptOut() {
        return null;
    }

    @Override
    public Integer getTargetedAdvertisingOptOutNotice() {
        return null;
    }

    @Override
    public Integer getSensitiveDataLimitUseNotice() {
        return ObjectUtil.getIfNotNull(consent, UsCa::getSensitiveDataLimitUseNotice);
    }

    @Override
    public List<Integer> getSensitiveDataProcessing() {
        final List<Integer> originalData = consent != null
                ? consent.getSensitiveDataProcessing()
                : DEFAULT_SENSITIVE_DATA;

        final List<Integer> data = new ArrayList<>(DEFAULT_SENSITIVE_DATA);
        data.set(0, originalData.get(3));
        data.set(1, originalData.get(3));
        data.set(2, originalData.get(7));
        data.set(3, originalData.get(8));
        data.set(5, originalData.get(5));
        data.set(6, originalData.get(6));
        data.set(7, originalData.get(2));
        data.set(8, originalData.get(0));
        data.set(9, originalData.get(1));
        data.set(11, originalData.get(4));

        return Collections.unmodifiableList(data);
    }

    @Override
    public Integer getSensitiveDataProcessingOptOutNotice() {
        return null;
    }

    @Override
    public List<Integer> getKnownChildSensitiveDataConsents() {
        final List<Integer> data = consent != null ? consent.getKnownChildSensitiveDataConsents() : null;
        return data != null && data.get(0) == 0 && data.get(1) == 0
                ? data
                : CHILD_SENSITIVE_DATA;
    }

    @Override
    public Integer getPersonalDataConsents() {
        return ObjectUtil.getIfNotNull(consent, UsCa::getPersonalDataConsents);
    }

    @Override
    public Integer getMspaCoveredTransaction() {
        return ObjectUtil.getIfNotNull(consent, UsCa::getMspaCoveredTransaction);
    }

    @Override
    public Integer getMspaServiceProviderMode() {
        return ObjectUtil.getIfNotNull(consent, UsCa::getMspaServiceProviderMode);
    }

    @Override
    public Integer getMspaOptOutOptionMode() {
        return ObjectUtil.getIfNotNull(consent, UsCa::getMspaOptOutOptionMode);
    }
}
