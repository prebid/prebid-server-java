package org.prebid.server.privacy.gdpr.vendorlist;

import com.iabtcf.decoder.TCString;
import com.iabtcf.encoder.TCStringEncoder;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.prebid.server.assertion.FutureAssertion.assertThat;

@ExtendWith(MockitoExtension.class)
public class VersionedVendorListServiceTest {

    private static final Instant NOW = Instant.parse("2024-06-01T12:00:00Z");

    private VersionedVendorListService target;

    @Mock
    private VendorListService vendorListServiceV2;
    @Mock
    private VendorListService vendorListServiceV3;
    @Mock
    private LiveVendorListService liveVendorListService;

    @BeforeEach
    public void setUp() {
        target = new VersionedVendorListService(
                vendorListServiceV2, vendorListServiceV3, liveVendorListService, Clock.fixed(NOW, ZoneOffset.UTC));
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
        given(vendorListServiceV2.forVersion(anyInt())).willReturn(Future.succeededFuture(emptyMap()));

        // when
        target.forConsent(consent);

        // then
        verify(vendorListServiceV2).forVersion(12);
    }

    @Test
    public void versionedVendorListServiceShouldTreatTcfPolicyGreaterOrEqualFourAsVendorListSpecificationThree() {
        // given
        final int tcfPolicyVersion = ThreadLocalRandom.current().nextInt(4, 64);
        final TCString consent = TCStringEncoder.newBuilder()
                .version(2)
                .tcfPolicyVersion(tcfPolicyVersion)
                .vendorListVersion(12)
                .toTCString();
        given(vendorListServiceV3.forVersion(anyInt())).willReturn(Future.succeededFuture(emptyMap()));

        // when
        target.forConsent(consent);

        // then
        verify(vendorListServiceV3).forVersion(12);
    }

    @Test
    public void forConsentShouldRemoveVendorsMarkedDeletedInRequestedGvl() {
        // given
        final Vendor deletedVendor = Vendor.empty(1).toBuilder()
                .deletedDate(Instant.parse("2024-01-01T00:00:00Z"))
                .build();
        final Vendor activeVendor = Vendor.empty(52);
        final Map<Integer, Vendor> vendorList = Map.of(1, deletedVendor, 52, activeVendor);
        final TCString consent = TCStringEncoder.newBuilder()
                .version(2)
                .tcfPolicyVersion(3)
                .vendorListVersion(12)
                .toTCString();

        given(vendorListServiceV2.forVersion(anyInt())).willReturn(Future.succeededFuture(vendorList));
        given(liveVendorListService.isDeleted(anyInt())).willReturn(false);

        // when and then
        assertThat(target.forConsent(consent))
                .isSucceeded()
                .unwrap()
                .satisfies(result -> {
                    assertThat(result).containsOnlyKeys(52);
                    assertThat(result.get(52)).isSameAs(activeVendor);
                });
    }

    @Test
    public void forConsentShouldRemoveVendorsMarkedDeletedInLiveGvl() {
        // given
        final Vendor deletedVendor = Vendor.empty(1);
        final Vendor activeVendor = Vendor.empty(52);
        final Map<Integer, Vendor> vendorList = Map.of(1, deletedVendor, 52, activeVendor);
        final TCString consent = TCStringEncoder.newBuilder()
                .version(2)
                .tcfPolicyVersion(3)
                .vendorListVersion(12)
                .toTCString();

        given(vendorListServiceV2.forVersion(anyInt())).willReturn(Future.succeededFuture(vendorList));
        given(liveVendorListService.isDeleted(1)).willReturn(true);
        given(liveVendorListService.isDeleted(52)).willReturn(false);

        // when and then
        assertThat(target.forConsent(consent))
                .isSucceeded()
                .unwrap()
                .satisfies(result -> {
                    assertThat(result).containsOnlyKeys(52);
                    assertThat(result.get(52)).isSameAs(activeVendor);
                });
    }
}
