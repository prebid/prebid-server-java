package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BrandVersion;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.UserAgent;
import fiftyone.pipeline.core.flowelements.Pipeline;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.execution.v1.auction.AuctionInvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.AccountFilter;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.ModuleConfig;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.FiftyOneDeviceDetectionModule;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceEnricher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.EnrichmentResult;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.ModuleContext;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;
import org.prebid.server.settings.model.Account;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class FiftyOneDeviceDetectionRawAuctionRequestHookTest {
    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private ModuleConfig moduleConfig;
    private RawAuctionRequestHook target;
    private BiFunction<Device, CollectedEvidence, EnrichmentResult> deviceRefiner;

    @Before
    public void setUp() {
        moduleConfig = new ModuleConfig();
        deviceRefiner = (bidRequest, evidence) -> null;
        target = new FiftyOneDeviceDetectionRawAuctionRequestHook(
                moduleConfig,
                new DeviceEnricher(mock(Pipeline.class)) {
                    @Override
                    public EnrichmentResult populateDeviceInfo(
                            Device device,
                            CollectedEvidence collectedEvidence) {
                        return deviceRefiner.apply(device, collectedEvidence);
                    }
                });
    }

    // MARK: - convertSecureHeaders

    @Test
    public void callShouldAddEmptyMapOfSecureHeadersWhenUserAgentIsEmpty() throws Exception {
        // given
        final UserAgent userAgent = UserAgent.builder().build();
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .sua(userAgent)
                        .build())
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
        final Map<String, String> evidence = ((ModuleContext) target.call(payload, auctionInvocationContext)
                .result()
                .moduleContext())
                .collectedEvidence()
                .secureHeaders();

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence).isEmpty();
    }

    @Test
    public void callShouldAddBrowsersToSecureHeaders() throws Exception {
        // given
        final UserAgent userAgent = UserAgent.builder()
                .browsers(List.of(
                        new BrandVersion("Nickel", List.of("6", "3", "1", "a"), null),
                        new BrandVersion(null, List.of("7", "52"), null), // should be skipped
                        new BrandVersion("FrostCat", List.of("9", "2", "5", "8"), null)
                ))
                .build();
        final String expectedBrowsers = "\"Nickel\";v=\"6.3.1.a\", \"FrostCat\";v=\"9.2.5.8\"";
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .sua(userAgent)
                        .build())
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
        final Map<String, String> evidence = ((ModuleContext) target.call(payload, auctionInvocationContext)
                .result()
                .moduleContext())
                .collectedEvidence()
                .secureHeaders();

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence.size()).isEqualTo(2);
        assertThat(evidence.get("header.Sec-CH-UA")).isEqualTo(expectedBrowsers);
        assertThat(evidence.get("header.Sec-CH-UA-Full-Version-List")).isEqualTo(expectedBrowsers);
    }

    @Test
    public void callShouldAddPlatformToSecureHeaders() throws Exception {
        final UserAgent userAgent = UserAgent.builder()
                .platform(new BrandVersion("Cyborg", List.of("19", "5"), null))
                .build();
        final String expectedPlatformName = "\"Cyborg\"";
        final String expectedPlatformVersion = "\"19.5\"";
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .sua(userAgent)
                        .build())
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
        final Map<String, String> evidence = ((ModuleContext) target.call(payload, auctionInvocationContext)
                .result()
                .moduleContext())
                .collectedEvidence()
                .secureHeaders();

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence.size()).isEqualTo(2);
        assertThat(evidence.get("header.Sec-CH-UA-Platform")).isEqualTo(expectedPlatformName);
        assertThat(evidence.get("header.Sec-CH-UA-Platform-Version")).isEqualTo(expectedPlatformVersion);
    }

    @Test
    public void callShouldAddIsMobileToSecureHeaders() throws Exception {
        final UserAgent userAgent = UserAgent.builder()
                .mobile(5)
                .build();
        final String expectedIsMobile = "?5";
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .sua(userAgent)
                        .build())
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
        final Map<String, String> evidence = ((ModuleContext) target.call(payload, auctionInvocationContext)
                .result()
                .moduleContext())
                .collectedEvidence()
                .secureHeaders();

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence.size()).isEqualTo(1);
        assertThat(evidence.get("header.Sec-CH-UA-Mobile")).isEqualTo(expectedIsMobile);
    }

    @Test
    public void callShouldAddArchitectureToSecureHeaders() throws Exception {
        final UserAgent userAgent = UserAgent.builder()
                .architecture("LEG")
                .build();
        final String expectedArchitecture = "\"LEG\"";
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .sua(userAgent)
                        .build())
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
        final Map<String, String> evidence = ((ModuleContext) target.call(payload, auctionInvocationContext)
                .result()
                .moduleContext())
                .collectedEvidence()
                .secureHeaders();

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence.size()).isEqualTo(1);
        assertThat(evidence.get("header.Sec-CH-UA-Arch")).isEqualTo(expectedArchitecture);
    }

    @Test
    public void callShouldAddBitnessToSecureHeaders() throws Exception {
        final UserAgent userAgent = UserAgent.builder()
                .bitness("doubtful")
                .build();
        final String expectedBitness = "\"doubtful\"";
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .sua(userAgent)
                        .build())
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
        final Map<String, String> evidence = ((ModuleContext) target.call(payload, auctionInvocationContext)
                .result()
                .moduleContext())
                .collectedEvidence()
                .secureHeaders();

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence.size()).isEqualTo(1);
        assertThat(evidence.get("header.Sec-CH-UA-Bitness")).isEqualTo(expectedBitness);
    }

    @Test
    public void callShouldAddModelToSecureHeaders() throws Exception {
        final UserAgent userAgent = UserAgent.builder()
                .model("reflectivity")
                .build();
        final String expectedModel = "\"reflectivity\"";
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .sua(userAgent)
                        .build())
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
        final Map<String, String> evidence = ((ModuleContext) target.call(payload, auctionInvocationContext)
                .result()
                .moduleContext())
                .collectedEvidence()
                .secureHeaders();

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence.size()).isEqualTo(1);
        assertThat(evidence.get("header.Sec-CH-UA-Model")).isEqualTo(expectedModel);
    }

    // MARK: - addEvidenceToContext

    @Test
    public void callShouldMakeNewContextWhenNullIsPassedIn() throws Exception {
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
    public void callShouldMakeNewEvidenceWhenNoneWasPresent() throws Exception {
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
    public void callShouldMergeEvidences() throws Exception {
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
    public void callShouldNotFailWhenNoDevice() throws Exception {
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
    public void callShouldAddUAToModuleContextEvidence() throws Exception {
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
    public void callShouldAddSUAToModuleContextEvidence() throws Exception {
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
    public void payloadUpdateShouldReturnNullWhenRequestIsNull() throws Exception {
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

        // when
        final boolean[] refinerCalled = {false};
        deviceRefiner = (device, evidence) -> {
            refinerCalled[0] = true;
            return EnrichmentResult.builder().build();
        };
        final BidRequest newBidRequest = target.call(auctionRequestPayload, invocationContext)
                .result()
                .payloadUpdate()
                .apply(auctionRequestPayload)
                .bidRequest();

        // then
        assertThat(newBidRequest).isEqualTo(bidRequest);
        assertThat(refinerCalled).containsExactly(true);
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

        // when
        final boolean[] refinerCalled = {false};
        deviceRefiner = (device, collectedEvidence) -> {
            assertThat(collectedEvidence.rawHeaders()).isEqualTo(savedEvidence.rawHeaders());
            assertThat(collectedEvidence.deviceUA()).isEqualTo(fakeUA);
            refinerCalled[0] = true;
            return null;
        };
        final BidRequest newBidRequest = target.call(auctionRequestPayload, invocationContext)
                .result()
                .payloadUpdate()
                .apply(auctionRequestPayload)
                .bidRequest();

        // then
        assertThat(newBidRequest).isEqualTo(bidRequest);
        assertThat(refinerCalled).containsExactly(true);
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

        // when
        deviceRefiner = (device, collectedEvidence) -> EnrichmentResult
                .builder()
                .enrichedDevice(mergedDevice)
                .build();
        final BidRequest newBidRequest = target.call(auctionRequestPayload, invocationContext)
                .result()
                .payloadUpdate()
                .apply(auctionRequestPayload)
                .bidRequest();

        // then
        assertThat(newBidRequest.getDevice()).isEqualTo(mergedDevice);
    }

    // MARK: - code

    @Test
    public void codeShouldStartWithModuleCode() throws Exception {
        // when and then
        assertThat(target.code()).startsWith(FiftyOneDeviceDetectionModule.CODE);
    }

    // MARK: - isAccountAllowed

    @Test
    public void callShouldReturnUpdateActionWhenFilterIsNull() throws Exception {
        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, null)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.update);
    }

    @Test
    public void callShouldReturnUpdateActionWhenNoWhitelistAndNoAuctionInvocationContext() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, null)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.update);
    }

    @Test
    public void callShouldReturnUpdateActionWhenWhitelistEmptyAndNoAuctionInvocationContext() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());
        moduleConfig.getAccountFilter().setAllowList(Collections.emptyList());

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, null)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.update);
    }

    @Test
    public void callShouldReturnNoUpdateActionWhenWhitelistFilledAndNoAuctionInvocationContext() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());
        moduleConfig.getAccountFilter().setAllowList(Collections.singletonList("42"));

        // when
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().build());
        final InvocationAction invocationAction = target.call(payload, null)
                .result()
                .action();

        // then
        assertThat(invocationAction).isEqualTo(InvocationAction.no_action);
    }

    @Test
    public void callShouldReturnUpdateActionWhenNoWhitelistAndNoAuctionContext() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());

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
    public void callShouldReturnUpdateActionWhenWhitelistEmptyAndNoAuctionContext() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());
        moduleConfig.getAccountFilter().setAllowList(Collections.emptyList());

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
    public void callShouldReturnNoUpdateActionWhenWhitelistFilledAndNoAuctionContext() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());
        moduleConfig.getAccountFilter().setAllowList(Collections.singletonList("42"));

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
    public void callShouldReturnUpdateActionWhenNoWhitelistAndNoAccount() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());

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
    public void callShouldReturnUpdateActionWhenWhitelistEmptyAndNoAccount() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());
        moduleConfig.getAccountFilter().setAllowList(Collections.emptyList());

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
    public void callShouldReturnNoUpdateActionWhenWhitelistFilledAndNoAccount() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());
        moduleConfig.getAccountFilter().setAllowList(Collections.singletonList("42"));

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
    public void callShouldReturnUpdateActionWhenNoWhitelistAndNoAccountID() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());

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
    public void callShouldReturnUpdateActionWhenWhitelistEmptyAndNoAccountID() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());
        moduleConfig.getAccountFilter().setAllowList(Collections.emptyList());

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
    public void callShouldReturnNoUpdateActionWhenWhitelistFilledAndNoAccountID() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());
        moduleConfig.getAccountFilter().setAllowList(Collections.singletonList("42"));

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
    public void callShouldReturnUpdateActionWhenNoWhitelistAndEmptyAccountID() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());

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
    public void callShouldReturnUpdateActionWhenWhitelistEmptyAndEmptyAccountID() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());
        moduleConfig.getAccountFilter().setAllowList(Collections.emptyList());

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
    public void callShouldReturnNoUpdateActionWhenWhitelistFilledAndEmptyAccountID() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());
        moduleConfig.getAccountFilter().setAllowList(Collections.singletonList("42"));

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
    public void callShouldReturnUpdateActionWhenNoWhitelistAndAllowedAccountID() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());

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
    public void callShouldReturnUpdateActionWhenWhitelistEmptyAndAllowedAccountID() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());
        moduleConfig.getAccountFilter().setAllowList(Collections.emptyList());

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
    public void callShouldReturnUpdateActionWhenWhitelistFilledAndAllowedAccountID() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());
        moduleConfig.getAccountFilter().setAllowList(Collections.singletonList("42"));

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
    public void callShouldReturnUpdateActionWhenNoWhitelistAndNotAllowedAccountID() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());

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
    public void callShouldReturnUpdateActionWhenWhitelistEmptyAndNotAllowedAccountID() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());
        moduleConfig.getAccountFilter().setAllowList(Collections.emptyList());

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
    public void callShouldReturnNoUpdateActionWhenWhitelistFilledAndNotAllowedAccountID() throws Exception {
        // given
        moduleConfig.setAccountFilter(new AccountFilter());
        moduleConfig.getAccountFilter().setAllowList(Collections.singletonList("42"));

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
