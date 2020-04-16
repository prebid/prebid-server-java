package org.prebid.server.privacy.gdpr;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.vendorlist.VendorListService;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorListV1;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorV1;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.prebid.server.assertion.FutureAssertion.assertThat;

public class GdprServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private VendorListService<VendorListV1, VendorV1> vendorListService;

    private GdprService gdprService;

    @Before
    public void setUp() {
        gdprService = new GdprService(vendorListService);
    }

    @Test
    public void shouldReturnAllDeniedWhenNoConsentParam() {
        assertThat(gdprService.resultFor(singleton(1), null))
                .succeededWith(singletonList(VendorPermission.of(1, null, denyAll())));
    }

    @Test
    public void shouldReturnAllDeniedWhenConsentParamIsInvalid() {
        assertThat(gdprService.resultFor(singleton(1), "invalid-consent"))
                .succeededWith(singletonList(VendorPermission.of(1, null, denyAll())));
    }

    @Test
    public void shouldReturnAllDeniedWhenVendorIsNotAllowed() {
        // given
        given(vendorListService.forVersion(anyInt())).willReturn(Future.succeededFuture(emptyMap()));

        // when and then
        assertThat(gdprService.resultFor(singleton(9), "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA"))
                .succeededWith(singletonList(VendorPermission.of(9, null, denyAll())));
    }

    @Test
    public void shouldReturnAllDeniedWhenVendorIsNotInVendorList() {
        // given
        given(vendorListService.forVersion(anyInt())).willReturn(Future.succeededFuture(emptyMap()));

        // when and then
        assertThat(gdprService.resultFor(singleton(1), "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA"))
                .succeededWith(singletonList(VendorPermission.of(1, null, denyAll())));
    }

    @Test
    public void shouldReturnAllDeniedWhenAllClaimedPurposesAreNotAllowed() {
        // given
        given(vendorListService.forVersion(anyInt())).willReturn(Future.succeededFuture(
                singletonMap(1, VendorV1.of(1, singleton(4), singleton(5)))));

        // when and then
        assertThat(gdprService.resultFor(singleton(1), "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA"))
                .succeededWith(singletonList(VendorPermission.of(1, null, denyAll())));
    }

    @Test
    public void shouldReturnPrivateInfoAllowedUserSyncDeniedWhenAllClaimedPurposesAreAllowed() {
        // given
        given(vendorListService.forVersion(anyInt())).willReturn(Future.succeededFuture(
                singletonMap(1, VendorV1.of(1, singleton(2), singleton(3)))));

        // when and then
        assertThat(gdprService.resultFor(singleton(1), "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA"))
                .succeededWith(singletonList(VendorPermission.of(1, null, action(true, false))));
    }

    @Test
    public void shouldReturnAllAllowedWhenAllClaimedPurposesAreAllowedIncludingPurposeOne() {
        // given
        given(vendorListService.forVersion(anyInt())).willReturn(Future.succeededFuture(
                singletonMap(1, VendorV1.of(1, singleton(1), singleton(2)))));

        // when and then
        assertThat(gdprService.resultFor(singleton(1), "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA"))
                .succeededWith(singletonList(VendorPermission.of(1, null, action(true, true))));
    }

    @Test
    public void shouldReturnPrivateInfoDeniedUserSyncAllowedWhenNotAllClaimedPurposesAreAllowedButPurposeOneIs() {
        // given
        given(vendorListService.forVersion(anyInt())).willReturn(Future.succeededFuture(
                singletonMap(1, VendorV1.of(1, singleton(1), singleton(4)))));

        // when and then
        assertThat(gdprService.resultFor(singleton(1), "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA"))
                .succeededWith(singletonList(VendorPermission.of(1, null, action(false, true))));
    }

    @Test
    public void shouldReturnUserSyncDeniedWhenPurposeOneIsAllowedButNotClaimed() {
        // given
        given(vendorListService.forVersion(anyInt())).willReturn(Future.succeededFuture(
                singletonMap(1, VendorV1.of(1, singleton(2), singleton(3)))));

        // when and then
        assertThat(gdprService.resultFor(singleton(1), "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA"))
                .succeededWith(singletonList(VendorPermission.of(1, null, action(true, false))));
    }

    @Test
    public void shouldReturnFailedFutureWhenGettingAllowedPurposesFails() {
        // given
        given(vendorListService.forVersion(anyInt())).willReturn(Future.succeededFuture(emptyMap()));

        // when and then
        assertThat(gdprService.resultFor(singleton(1), "BONciguONcjGKADACHENAOLS1r"))
                .isFailed()
                .hasMessage("Error when retrieving allowed purpose ids in a reason of invalid consent string");
    }

    @Test
    public void shouldReturnFailedFutureWhenGettingIfVendorAllowedFails() {
        // given
        given(vendorListService.forVersion(anyInt())).willReturn(Future.succeededFuture(emptyMap()));

        // when and then
        assertThat(gdprService.resultFor(singleton(1), "BOSbaBZOSbaBoABABBENBcoAAAAgSABgBAA"))
                .isFailed()
                .hasMessage("Error when checking if vendor is allowed in a reason of invalid consent string");
    }

    private static PrivacyEnforcementAction denyAll() {
        return action(false, false);
    }

    private static PrivacyEnforcementAction action(boolean allowPrivateInfo, boolean allowUserSync) {
        return PrivacyEnforcementAction.builder()
                .removeUserBuyerUid(!allowPrivateInfo)
                .maskGeo(!allowPrivateInfo)
                .maskDeviceIp(!allowPrivateInfo)
                .maskDeviceInfo(!allowPrivateInfo)
                .blockAnalyticsReport(false)
                .blockBidderRequest(false)
                .blockPixelSync(!allowUserSync)
                .build();
    }
}
