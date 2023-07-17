package org.prebid.server.privacy.gdpr.vendorlist;

import com.iabtcf.decoder.TCString;
import com.iabtcf.encoder.TCStringEncoder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.ThreadLocalRandom;

import static org.mockito.Mockito.verify;
import static org.prebid.server.assertion.FutureAssertion.assertThat;

public class VersionedVendorListServiceTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private VersionedVendorListService versionedVendorListService;

    @Mock
    private VendorListService vendorListServiceV2;
    @Mock
    private VendorListService vendorListServiceV3;

    @Before
    public void setUp() {
        versionedVendorListService = new VersionedVendorListService(vendorListServiceV2, vendorListServiceV3);
    }

    @Test
    public void versionedVendorListServiceShouldTreatTcfPolicyLessThanFourAsVendorListSpecificationTwo() {
        // given
        final int tcfPolicyVersion = ThreadLocalRandom.current().nextInt(0, 4);
        final TCString consent = TCStringEncoder.newBuilder()
                .version(2)
                .tcfPolicyVersion(tcfPolicyVersion)
                .vendorListVersion(12)
                .toTCString();

        // when
        versionedVendorListService.forConsent(consent);

        // then
        verify(vendorListServiceV2).forVersion(12);
    }

    @Test
    public void versionedVendorListServiceShouldTreatTcfPolicyFourAsVendorListSpecificationThree() {
        // given
        final TCString consent = TCStringEncoder.newBuilder()
                .version(2)
                .tcfPolicyVersion(4)
                .vendorListVersion(12)
                .toTCString();

        // when
        versionedVendorListService.forConsent(consent);

        // then
        verify(vendorListServiceV3).forVersion(12);
    }

    @Test
    public void versionedVendorListServiceShouldTreatTcfPolicyGreaterThanFourAsInvalidVersion() {
        // given
        final int tcfPolicyVersion = ThreadLocalRandom.current().nextInt(5, 63);
        final TCString consent = TCStringEncoder.newBuilder()
                .version(2)
                .tcfPolicyVersion(tcfPolicyVersion)
                .vendorListVersion(12)
                .toTCString();

        // when and then
        assertThat(versionedVendorListService.forConsent(consent))
                .isFailed()
                .hasMessage("Invalid tcf policy version: " + tcfPolicyVersion);
    }
}
