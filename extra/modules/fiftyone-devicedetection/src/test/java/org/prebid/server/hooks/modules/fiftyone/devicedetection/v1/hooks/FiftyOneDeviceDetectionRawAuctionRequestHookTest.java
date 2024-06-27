package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks;

import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.UserAgent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.execution.v1.auction.AuctionInvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.AccountFilter;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.FiftyOneDeviceDetectionModule;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceEnricher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.EnrichmentResult;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.ModuleContext;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.settings.model.Account;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FiftyOneDeviceDetectionRawAuctionRequestHookTest {
    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private DeviceEnricher deviceEnricher;
    private AccountFilter accountFilter;
    private RawAuctionRequestHook target;

    @Before
    public void setUp() {
        accountFilter = new AccountFilter();
        target = new FiftyOneDeviceDetectionRawAuctionRequestHook(accountFilter, deviceEnricher);
    }

    // MARK: - addEvidenceToContext

    @Test
    public void callShouldMakeNewContextWhenNullIsPassedIn() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(null)
                .build();
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);
        final AuctionInvocationContext invocationContext = AuctionInvocationContextImpl.of(
                null,
                null,
                false,
                null,
                null
        );

        // when
        final ModuleContext newContext = (ModuleContext) target.call(auctionRequestPayload, invocationContext)
                .result()
                .moduleContext();

        // then
        assertThat(newContext).isNotNull();
        assertThat(newContext.collectedEvidence()).isNotNull();
    }

    @Test
    public void callShouldMakeNewEvidenceWhenNoneWasPresent() {
        // given
        final ModuleContext moduleContext = ModuleContext.builder().build();
        final BidRequest bidRequest = BidRequest.builder()
                .device(null)
                .build();
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);
        final AuctionInvocationContext invocationContext = AuctionInvocationContextImpl.of(
                null,
                null,
                false,
                null,
                moduleContext
        );

        // when
        final ModuleContext newContext = (ModuleContext) target.call(auctionRequestPayload, invocationContext)
                .result()
                .moduleContext();

        // then
        assertThat(newContext).isNotNull();
        assertThat(newContext.collectedEvidence()).isNotNull();
    }

    @Test
    public void callShouldMergeEvidences() {
        // given
        final String ua = "mad-hatter";
        final HashMap<String, String> sua = new HashMap<>();
        final ModuleContext existingContext = ModuleContext.builder()
                .collectedEvidence(CollectedEvidence.builder()
                        .secureHeaders(sua)
                        .build())
                .build();
        final Device device = Device.builder().ua(ua).build();
        final BidRequest bidRequest = BidRequest.builder()
                .device(device)
                .build();
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);
        final AuctionInvocationContext invocationContext = AuctionInvocationContextImpl.of(
                null,
                null,
                false,
                null,
                existingContext
        );

        // when
        final ModuleContext newContext = (ModuleContext) target.call(auctionRequestPayload, invocationContext)
                .result()
                .moduleContext();

        // then
        assertThat(newContext).isNotNull();
        final CollectedEvidence newEvidence = newContext.collectedEvidence();
        assertThat(newEvidence).isNotNull();
        assertThat(newEvidence.deviceUA()).isEqualTo(ua);
        assertThat(newEvidence.secureHeaders()).isEqualTo(sua);
    }

    // MARK: - collectEvidence

    @Test
    public void callShouldNotFailWhenNoDevice() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(bidRequest);
        final AuctionInvocationContext auctionInvocationContext = AuctionInvocationContextImpl.of(
                null,
                null,
                false,
                null,
                null
        );

        // when
        final CollectedEvidence evidence = ((ModuleContext) target.call(payload, auctionInvocationContext)
                .result()
                .moduleContext())
                .collectedEvidence();

        // then
        assertThat(evidence).isNotNull();
    }

    @Test
    public void callShouldAddUAToModuleContextEvidence() {
        // given
        final String testUA = "MindScape Crawler";
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua(testUA).build())
                .build();
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(bidRequest);
        final AuctionInvocationContext auctionInvocationContext = AuctionInvocationContextImpl.of(
                null,
                null,
                false,
                null,
                null
        );

        // when
        final CollectedEvidence evidence = ((ModuleContext) target.call(payload, auctionInvocationContext)
                .result()
                .moduleContext())
                .collectedEvidence();

        // then
        assertThat(evidence.deviceUA()).isEqualTo(testUA);
    }

    @Test
    public void callShouldAddSUAToModuleContextEvidence() {
        // given
        final UserAgent testSUA = UserAgent.builder().build();
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().sua(testSUA).build())
                .build();
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(bidRequest);
        final AuctionInvocationContext auctionInvocationContext = AuctionInvocationContextImpl.of(
                null,
                null,
                false,
                null,
                null
        );

        // when
        final CollectedEvidence evidence = ((ModuleContext) target.call(payload, auctionInvocationContext)
                .result()
                .moduleContext())
                .collectedEvidence();

        // then
        assertThat(evidence.secureHeaders()).isEmpty();
    }

    // MARK: - enrichDevice

    @Test
    public void payloadUpdateShouldReturnNullWhenRequestIsNull() {
        // given
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(null);
        final AuctionInvocationContext invocationContext = AuctionInvocationContextImpl.of(
                null,
                null,
                false,
                null,
                ModuleContext.builder()
                        .collectedEvidence(null)
                        .build()
        );

        // when
        final BidRequest newBidRequest = target.call(auctionRequestPayload, invocationContext)
                .result()
                .payloadUpdate()
                .apply(auctionRequestPayload)
                .bidRequest();

        // then
        assertThat(newBidRequest).isNull();
    }

    @Test
    public void payloadUpdateShouldReturnOldRequestWhenPopulateDeviceInfoThrows() throws Exception {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final CollectedEvidence savedEvidence = CollectedEvidence.builder().build();
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);
        final AuctionInvocationContext invocationContext = AuctionInvocationContextImpl.of(
                null,
                null,
                false,
                null,
                ModuleContext.builder()
                        .collectedEvidence(savedEvidence)
                        .build()
        );
        final Exception e = new RuntimeException();
        when(deviceEnricher.populateDeviceInfo(any(), any())).thenThrow(e);

        // when
        final BidRequest newBidRequest = target.call(auctionRequestPayload, invocationContext)
                .result()
                .payloadUpdate()
                .apply(auctionRequestPayload)
                .bidRequest();

        // then
        assertThat(newBidRequest).isEqualTo(bidRequest);
        verify(deviceEnricher, times(1)).populateDeviceInfo(any(), any());
    }

    @Test
    public void payloadUpdateShouldReturnOldRequestWhenMergedDeviceIsNull() throws Exception {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final CollectedEvidence savedEvidence = CollectedEvidence.builder().build();
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);
        final AuctionInvocationContext invocationContext = AuctionInvocationContextImpl.of(
                null,
                null,
                false,
                null,
                ModuleContext.builder()
                        .collectedEvidence(savedEvidence)
                        .build()
        );
        when(deviceEnricher.populateDeviceInfo(any(), any()))
                .thenReturn(EnrichmentResult.builder().build());

        // when
        final BidRequest newBidRequest = target.call(auctionRequestPayload, invocationContext)
                .result()
                .payloadUpdate()
                .apply(auctionRequestPayload)
                .bidRequest();

        // then
        assertThat(newBidRequest).isEqualTo(bidRequest);
        verify(deviceEnricher, times(1)).populateDeviceInfo(any(), any());
    }

    @Test
    public void payloadUpdateShouldPassMergedEvidenceToDeviceRefiner() throws Exception {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final String fakeUA = "crystal-ball-navigator";
        final CollectedEvidence savedEvidence = CollectedEvidence.builder()
                .rawHeaders(Collections.emptySet())
                .deviceUA(fakeUA)
                .build();
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);
        final AuctionInvocationContext invocationContext = AuctionInvocationContextImpl.of(
                null,
                null,
                false,
                null,
                ModuleContext.builder()
                        .collectedEvidence(savedEvidence)
                        .build()
        );
        when(deviceEnricher.populateDeviceInfo(any(), any()))
                .thenReturn(EnrichmentResult.builder().build());

        // when
        final BidRequest newBidRequest = target.call(auctionRequestPayload, invocationContext)
                .result()
                .payloadUpdate()
                .apply(auctionRequestPayload)
                .bidRequest();

        // then
        assertThat(newBidRequest).isEqualTo(bidRequest);
        verify(deviceEnricher, times(1)).populateDeviceInfo(any(), any());

        final ArgumentCaptor<CollectedEvidence> evidenceCaptor = ArgumentCaptor.forClass(CollectedEvidence.class);
        verify(deviceEnricher).populateDeviceInfo(any(), evidenceCaptor.capture());
        final List<CollectedEvidence> allEvidences = evidenceCaptor.getAllValues();
        assertThat(allEvidences).hasSize(1);
        assertThat(allEvidences.getFirst().deviceUA()).isEqualTo(fakeUA);
    }

    @Test
    public void payloadUpdateShouldInjectReturnedDevice() throws Exception {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final CollectedEvidence savedEvidence = CollectedEvidence.builder().build();
        final Device mergedDevice = Device.builder().build();
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);
        final AuctionInvocationContext invocationContext = AuctionInvocationContextImpl.of(
                null,
                null,
                false,
                null,
                ModuleContext.builder()
                        .collectedEvidence(savedEvidence)
                        .build()
        );
        when(deviceEnricher.populateDeviceInfo(any(), any()))
                .thenReturn(EnrichmentResult
                        .builder()
                        .enrichedDevice(mergedDevice)
                        .build());

        // when
        final BidRequest newBidRequest = target.call(auctionRequestPayload, invocationContext)
                .result()
                .payloadUpdate()
                .apply(auctionRequestPayload)
                .bidRequest();

        // then
        assertThat(newBidRequest.getDevice()).isEqualTo(mergedDevice);
        verify(deviceEnricher, times(1)).populateDeviceInfo(any(), any());
    }

    // MARK: - code

    @Test
    public void codeShouldStartWithModuleCode() {
        // when and then
        assertThat(target.code()).startsWith(FiftyOneDeviceDetectionModule.CODE);
    }

    // MARK: - isAccountAllowed

    @Test
    public void callShouldReturnUpdateActionWhenFilterIsNull() {
        // given
        final AuctionInvocationContext context = AuctionInvocationContextImpl.of(
                null,
                null,
                false,
                null,
                null
        );

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, context)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.update);
    }

    @Test
    public void callShouldReturnUpdateActionWhenNoWhitelistAndNoAuctionContext() {
        // given

        final AuctionInvocationContext context = AuctionInvocationContextImpl.of(
                null,
                null,
                false,
                null,
                null
        );

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, context)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.update);
    }

    @Test
    public void callShouldReturnUpdateActionWhenWhitelistEmptyAndNoAuctionContext() {
        // given
        accountFilter.setAllowList(Collections.emptyList());

        final AuctionInvocationContext context = AuctionInvocationContextImpl.of(
                null,
                null,
                false,
                null,
                null
        );

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, context)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.update);
    }

    @Test
    public void callShouldReturnNoUpdateActionWhenWhitelistFilledAndNoAuctionContext() {
        // given
        accountFilter.setAllowList(Collections.singletonList("42"));

        final AuctionInvocationContext context = AuctionInvocationContextImpl.of(
                null,
                null,
                false,
                null,
                null
        );

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, context)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.no_action);
    }

    @Test
    public void callShouldReturnUpdateActionWhenNoWhitelistAndNoAccount() {
        // given

        final AuctionContext auctionContext = AuctionContext.builder().build();
        final AuctionInvocationContext context = AuctionInvocationContextImpl.of(
                null,
                auctionContext,
                false,
                null,
                null
        );

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, context)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.update);
    }

    @Test
    public void callShouldReturnNoUpdateActionWhenNoWhitelistAndNoAccountButDeviceIdIsSet() {
        // given

        final AuctionContext auctionContext = AuctionContext.builder().build();
        final AuctionInvocationContext context = AuctionInvocationContextImpl.of(
                null,
                auctionContext,
                false,
                null,
                null
        );
        final ExtDevice ext = ExtDevice.empty();
        final Device device = Device.builder().ext(ext).build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(bidRequest);
        ext.addProperty("fiftyonedegrees_deviceId", new TextNode("0-0-0-0"));

        // when
        final InvocationAction invocationAction = target.call(payload, context)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.no_action);
    }

    @Test
    public void callShouldReturnUpdateActionWhenWhitelistEmptyAndNoAccount() {
        // given
        accountFilter.setAllowList(Collections.emptyList());

        final AuctionContext auctionContext = AuctionContext.builder().build();
        final AuctionInvocationContext context = AuctionInvocationContextImpl.of(
                null,
                auctionContext,
                false,
                null,
                null
        );

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, context)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.update);
    }

    @Test
    public void callShouldReturnNoUpdateActionWhenWhitelistFilledAndNoAccount() {
        // given
        accountFilter.setAllowList(Collections.singletonList("42"));

        final AuctionContext auctionContext = AuctionContext.builder().build();
        final AuctionInvocationContext context = AuctionInvocationContextImpl.of(
                null,
                auctionContext,
                false,
                null,
                null
        );

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, context)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.no_action);
    }

    @Test
    public void callShouldReturnUpdateActionWhenNoWhitelistAndNoAccountID() {
        // given

        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .build())
                .build();
        final AuctionInvocationContext context = AuctionInvocationContextImpl.of(
                null,
                auctionContext,
                false,
                null,
                null
        );

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, context)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.update);
    }

    @Test
    public void callShouldReturnUpdateActionWhenWhitelistEmptyAndNoAccountID() {
        // given
        accountFilter.setAllowList(Collections.emptyList());

        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .build())
                .build();
        final AuctionInvocationContext context = AuctionInvocationContextImpl.of(
                null,
                auctionContext,
                false,
                null,
                null
        );

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, context)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.update);
    }

    @Test
    public void callShouldReturnNoUpdateActionWhenWhitelistFilledAndNoAccountID() {
        // given
        accountFilter.setAllowList(Collections.singletonList("42"));

        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .build())
                .build();
        final AuctionInvocationContext context = AuctionInvocationContextImpl.of(
                null,
                auctionContext,
                false,
                null,
                null
        );

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, context)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.no_action);
    }

    @Test
    public void callShouldReturnUpdateActionWhenNoWhitelistAndEmptyAccountID() {
        // given

        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id("")
                        .build())
                .build();
        final AuctionInvocationContext context = AuctionInvocationContextImpl.of(
                null,
                auctionContext,
                false,
                null,
                null
        );

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, context)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.update);
    }

    @Test
    public void callShouldReturnUpdateActionWhenWhitelistEmptyAndEmptyAccountID() {
        // given
        accountFilter.setAllowList(Collections.emptyList());

        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id("")
                        .build())
                .build();
        final AuctionInvocationContext context = AuctionInvocationContextImpl.of(
                null,
                auctionContext,
                false,
                null,
                null
        );

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, context)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.update);
    }

    @Test
    public void callShouldReturnNoUpdateActionWhenWhitelistFilledAndEmptyAccountID() {
        // given
        accountFilter.setAllowList(Collections.singletonList("42"));

        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id("")
                        .build())
                .build();
        final AuctionInvocationContext context = AuctionInvocationContextImpl.of(
                null,
                auctionContext,
                false,
                null,
                null
        );

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, context)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.no_action);
    }

    @Test
    public void callShouldReturnUpdateActionWhenNoWhitelistAndAllowedAccountID() {
        // given

        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id("42")
                        .build())
                .build();
        final AuctionInvocationContext context = AuctionInvocationContextImpl.of(
                null,
                auctionContext,
                false,
                null,
                null
        );

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, context)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.update);
    }

    @Test
    public void callShouldReturnUpdateActionWhenWhitelistEmptyAndAllowedAccountID() {
        // given
        accountFilter.setAllowList(Collections.emptyList());

        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id("42")
                        .build())
                .build();
        final AuctionInvocationContext context = AuctionInvocationContextImpl.of(
                null,
                auctionContext,
                false,
                null,
                null
        );

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, context)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.update);
    }

    @Test
    public void callShouldReturnUpdateActionWhenWhitelistFilledAndAllowedAccountID() {
        // given
        accountFilter.setAllowList(Collections.singletonList("42"));

        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id("42")
                        .build())
                .build();
        final AuctionInvocationContext context = AuctionInvocationContextImpl.of(
                null,
                auctionContext,
                false,
                null,
                null
        );

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, context)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.update);
    }

    @Test
    public void callShouldReturnUpdateActionWhenNoWhitelistAndNotAllowedAccountID() {
        // given

        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id("29")
                        .build())
                .build();
        final AuctionInvocationContext context = AuctionInvocationContextImpl.of(
                null,
                auctionContext,
                false,
                null,
                null
        );

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, context)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.update);
    }

    @Test
    public void callShouldReturnUpdateActionWhenWhitelistEmptyAndNotAllowedAccountID() {
        // given
        accountFilter.setAllowList(Collections.emptyList());

        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id("29")
                        .build())
                .build();
        final AuctionInvocationContext context = AuctionInvocationContextImpl.of(
                null,
                auctionContext,
                false,
                null,
                null
        );

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, context)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.update);
    }

    @Test
    public void callShouldReturnNoUpdateActionWhenWhitelistFilledAndNotAllowedAccountID() {
        // given
        accountFilter.setAllowList(Collections.singletonList("42"));

        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .id("29")
                        .build())
                .build();
        final AuctionInvocationContext context = AuctionInvocationContextImpl.of(
                null,
                auctionContext,
                false,
                null,
                null
        );

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, context)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.no_action);
    }
}
