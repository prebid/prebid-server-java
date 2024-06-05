package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.rawAcutionRequest;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BrandVersion;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.UserAgent;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.ModuleConfig;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceEnricher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.FiftyOneDeviceDetectionRawAuctionRequestHook;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.ModuleContext;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UserAgentEvidenceConverterImpTest {
    private static BiConsumer<UserAgent, Map<String, String>> buildConverter() throws Exception {
        final RawAuctionRequestHook hook = new FiftyOneDeviceDetectionRawAuctionRequestHook(
                mock(ModuleConfig.class),
                mock(DeviceEnricher.class)
        );
        final AuctionRequestPayload payload = mock(AuctionRequestPayload.class);
        final AuctionInvocationContext auctionInvocationContext = mock(AuctionInvocationContext.class);
        return (userAgent, evidence) -> {
            final BidRequest bidRequest = BidRequest.builder()
                    .device(Device.builder()
                            .sua(userAgent)
                            .build())
                    .build();
            when(payload.bidRequest()).thenReturn(bidRequest);
            evidence.putAll(((ModuleContext) hook.call(payload, auctionInvocationContext)
                    .result()
                    .moduleContext())
                    .collectedEvidence()
                    .secureHeaders());
        };
    }

    @Test
    public void shouldReturnEmptyMapOnEmptyUserAgent() throws Exception {
        // given
        final UserAgent userAgent = UserAgent.builder().build();

        // when
        final Map<String, String> evidence = new HashMap<>();
        buildConverter().accept(userAgent, evidence);

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence).isEmpty();
    }

    @Test
    public void shouldAddBrowsers() throws Exception {
        // given
        final UserAgent userAgent = UserAgent.builder()
                .browsers(List.of(
                        new BrandVersion("Nickel", List.of("6", "3", "1", "a"), null),
                        new BrandVersion(null, List.of("7", "52"), null), // should be skipped
                        new BrandVersion("FrostCat", List.of("9", "2", "5", "8"), null)
                ))
                .build();
        final String expectedBrowsers = "\"Nickel\";v=\"6.3.1.a\", \"FrostCat\";v=\"9.2.5.8\"";

        // when
        final Map<String, String> evidence = new HashMap<>();
        buildConverter().accept(userAgent, evidence);

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence.size()).isEqualTo(2);
        assertThat(evidence.get("header.Sec-CH-UA")).isEqualTo(expectedBrowsers);
        assertThat(evidence.get("header.Sec-CH-UA-Full-Version-List")).isEqualTo(expectedBrowsers);
    }

    @Test
    public void shouldAddPlatform() throws Exception {
        final UserAgent userAgent = UserAgent.builder()
                .platform(new BrandVersion("Cyborg", List.of("19", "5"), null))
                .build();
        final String expectedPlatformName = "\"Cyborg\"";
        final String expectedPlatformVersion = "\"19.5\"";

        // when
        final Map<String, String> evidence = new HashMap<>();
        buildConverter().accept(userAgent, evidence);

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence.size()).isEqualTo(2);
        assertThat(evidence.get("header.Sec-CH-UA-Platform")).isEqualTo(expectedPlatformName);
        assertThat(evidence.get("header.Sec-CH-UA-Platform-Version")).isEqualTo(expectedPlatformVersion);
    }

    @Test
    public void shouldAddIsMobile() throws Exception {
        final UserAgent userAgent = UserAgent.builder()
                .mobile(5)
                .build();
        final String expectedIsMobile = "?5";

        // when
        final Map<String, String> evidence = new HashMap<>();
        buildConverter().accept(userAgent, evidence);

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence.size()).isEqualTo(1);
        assertThat(evidence.get("header.Sec-CH-UA-Mobile")).isEqualTo(expectedIsMobile);
    }

    @Test
    public void shouldAddArchitecture() throws Exception {
        final UserAgent userAgent = UserAgent.builder()
                .architecture("LEG")
                .build();
        final String expectedArchitecture = "\"LEG\"";

        // when
        final Map<String, String> evidence = new HashMap<>();
        buildConverter().accept(userAgent, evidence);

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence.size()).isEqualTo(1);
        assertThat(evidence.get("header.Sec-CH-UA-Arch")).isEqualTo(expectedArchitecture);
    }

    @Test
    public void shouldAddtBitness() throws Exception {
        final UserAgent userAgent = UserAgent.builder()
                .bitness("doubtful")
                .build();
        final String expectedBitness = "\"doubtful\"";

        // when
        final Map<String, String> evidence = new HashMap<>();
        buildConverter().accept(userAgent, evidence);

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence.size()).isEqualTo(1);
        assertThat(evidence.get("header.Sec-CH-UA-Bitness")).isEqualTo(expectedBitness);
    }

    @Test
    public void shouldAddModel() throws Exception {
        final UserAgent userAgent = UserAgent.builder()
                .model("reflectivity")
                .build();
        final String expectedModel = "\"reflectivity\"";

        // when
        final Map<String, String> evidence = new HashMap<>();
        buildConverter().accept(userAgent, evidence);

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence.size()).isEqualTo(1);
        assertThat(evidence.get("header.Sec-CH-UA-Model")).isEqualTo(expectedModel);
    }
}
