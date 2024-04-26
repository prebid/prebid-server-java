package org.prebid.server.bidder;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.MultiMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.spring.config.bidder.model.CompressionType;
import org.prebid.server.spring.config.bidder.model.Ortb;
import org.prebid.server.version.PrebidVersionProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class HttpBidderRequestEnricherTest {

    private static final String BIDDER_NAME = "bidderName";

    private static final String BIDDER_ALIAS_NAME = "bidderAliasName";

    @Mock
    private PrebidVersionProvider prebidVersionProvider;

    @Mock
    private BidderAliases bidderAliases;

    @Mock(strictness = LENIENT)
    private BidderCatalog bidderCatalog;

    private HttpBidderRequestEnricher target;

    @BeforeEach
    public void setUp() {
        given(prebidVersionProvider.getNameVersionRecord()).willReturn("pbs-java/1.00");
        given(bidderCatalog.bidderInfoByName(anyString())).willReturn(null);

        target = new HttpBidderRequestEnricher(prebidVersionProvider, bidderCatalog);
    }

    @Test
    public void shouldSendPopulatedPostRequest() {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("header1", "value1");
        headers.add("header2", "value2");

        // when
        final MultiMap resultHeaders = target.enrichHeaders(
                BIDDER_NAME, headers, CaseInsensitiveMultiMap.empty(), bidderAliases, BidRequest.builder().build());

        // then
        final MultiMap expectedHeaders = MultiMap.caseInsensitiveMultiMap();
        expectedHeaders.addAll(headers);
        expectedHeaders.add("x-prebid", "pbs-java/1.00");
        assertThat(resultHeaders).hasSize(3);
        assertThat(isEqualsMultiMaps(resultHeaders, expectedHeaders)).isTrue();
    }

    @Test
    public void shouldAddSecGpcHeaderFromOriginalRequest() {
        // given
        final CaseInsensitiveMultiMap originalHeaders = CaseInsensitiveMultiMap.builder()
                .add("Sec-GPC", "1")
                .add("Save-Data", "2")
                .add("Sec-CH-UA", "3")
                .add("Sec-CH-UA-Mobile", "4")
                .add("Sec-CH-UA-Platform", "5")
                .build();

        // when
        final MultiMap resultHeaders = target.enrichHeaders(
                BIDDER_NAME,
                MultiMap.caseInsensitiveMultiMap(),
                originalHeaders,
                bidderAliases,
                BidRequest.builder().build());

        // then
        assertThat(resultHeaders.get("Sec-GPC")).isEqualTo("1");
        assertThat(resultHeaders.get("Save-Data")).isEqualTo("2");
        assertThat(resultHeaders.get("Sec-CH-UA")).isEqualTo("3");
        assertThat(resultHeaders.get("Sec-CH-UA-Mobile")).isEqualTo("4");
        assertThat(resultHeaders.get("Sec-CH-UA-Platform")).isEqualTo("5");
    }

    @Test
    public void shouldNotOverrideHeadersFromBidRequest() {
        // given
        final CaseInsensitiveMultiMap originalHeaders = CaseInsensitiveMultiMap.builder()
                .add("Sec-GPC", "1")
                .add("Save-Data", "2")
                .add("Sec-CH-UA", "3")
                .add("Sec-CH-UA-Mobile", "4")
                .add("Sec-CH-UA-Platform", "5")
                .build();

        final MultiMap bidderRequestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add("Sec-GPC", "0")
                .add("Save-Data", "0")
                .add("Sec-CH-UA", "0")
                .add("Sec-CH-UA-Mobile", "0")
                .add("Sec-CH-UA-Platform", "0");

        // when
        final MultiMap resultHeaders = target.enrichHeaders(
                BIDDER_NAME, bidderRequestHeaders, originalHeaders, bidderAliases, BidRequest.builder().build());

        // then
        assertThat(resultHeaders.get("Sec-GPC")).isEqualTo("0");
        assertThat(resultHeaders.get("Save-Data")).isEqualTo("0");
        assertThat(resultHeaders.get("Sec-CH-UA")).isEqualTo("0");
        assertThat(resultHeaders.get("Sec-CH-UA-Mobile")).isEqualTo("0");
        assertThat(resultHeaders.get("Sec-CH-UA-Platform")).isEqualTo("0");
    }

    @Test
    public void shouldCreateXPrebidHeaderForOutgoingRequest() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .channel(ExtRequestPrebidChannel.of("pbjs", "4.39"))
                        .build()))
                .app(App.builder()
                        .ext(ExtApp.of(ExtAppPrebid.of("prebid-mobile", "1.2.3"), null))
                        .build())
                .build();

        // when
        final MultiMap resultHeaders = target.enrichHeaders(
                BIDDER_NAME,
                MultiMap.caseInsensitiveMultiMap(),
                CaseInsensitiveMultiMap.empty(),
                bidderAliases,
                bidRequest);

        // then
        final MultiMap expectedHeaders = MultiMap.caseInsensitiveMultiMap();
        expectedHeaders.add("x-prebid", "pbjs/4.39,prebid-mobile/1.2.3,pbs-java/1.00");
        assertThat(isEqualsMultiMaps(resultHeaders, expectedHeaders)).isTrue();
    }

    @Test
    public void shouldAddContentEncodingHeaderIfRequiredByBidderConfig() {
        // given
        when(bidderAliases.resolveBidder(BIDDER_NAME)).thenReturn(BIDDER_NAME);
        when(bidderCatalog.bidderInfoByName(eq(BIDDER_NAME))).thenReturn(BidderInfo.create(
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                false,
                false,
                CompressionType.GZIP,
                Ortb.of(false)));

        final CaseInsensitiveMultiMap originalHeaders = CaseInsensitiveMultiMap.builder().build();

        // when
        final MultiMap resultHeaders = target
                .enrichHeaders(
                        BIDDER_NAME,
                        MultiMap.caseInsensitiveMultiMap(),
                        originalHeaders,
                        bidderAliases,
                        BidRequest.builder().build());

        // then
        assertThat(resultHeaders.contains("Content-Encoding")).isTrue();
        assertThat(resultHeaders.get("Content-Encoding")).isEqualTo("gzip");
    }

    @Test
    public void shouldAddContentEncodingHeaderIfRequiredByBidderAliasConfig() {
        // given
        when(bidderAliases.resolveBidder(BIDDER_ALIAS_NAME)).thenReturn(BIDDER_NAME);
        when(bidderCatalog.bidderInfoByName(eq(BIDDER_NAME))).thenReturn(BidderInfo.create(
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                false,
                false,
                CompressionType.GZIP,
                Ortb.of(false)));

        final CaseInsensitiveMultiMap originalHeaders = CaseInsensitiveMultiMap.builder().build();

        // when
        final MultiMap resultHeaders = target
                .enrichHeaders(
                        BIDDER_ALIAS_NAME,
                        MultiMap.caseInsensitiveMultiMap(),
                        originalHeaders,
                        bidderAliases,
                        BidRequest.builder().build());

        // then
        assertThat(resultHeaders.contains("Content-Encoding")).isTrue();
        assertThat(resultHeaders.get("Content-Encoding")).isEqualTo("gzip");
    }

    private static boolean isEqualsMultiMaps(MultiMap left, MultiMap right) {
        return left.size() == right.size() && left.entries().stream()
                .allMatch(entry -> right.contains(entry.getKey(), entry.getValue(), true));
    }
}
