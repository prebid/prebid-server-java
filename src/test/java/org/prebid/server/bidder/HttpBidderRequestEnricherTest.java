package org.prebid.server.bidder;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.MultiMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.spring.config.bidder.model.CompressionType;
import org.prebid.server.version.PrebidVersionProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

public class HttpBidderRequestEnricherTest {

    private static final String BIDDER_NAME = "bidderName";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private PrebidVersionProvider prebidVersionProvider;

    @Mock
    private BidderCatalog bidderCatalog;

    private HttpBidderRequestEnricher requestEnricher;

    @Before
    public void setUp() {
        given(prebidVersionProvider.getNameVersionRecord()).willReturn("pbs-java/1.00");
        given(bidderCatalog.bidderInfoByName(anyString())).willReturn(null);

        requestEnricher = new HttpBidderRequestEnricher(prebidVersionProvider, bidderCatalog);
    }

    @Test
    public void shouldSendPopulatedPostRequest() {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("header1", "value1");
        headers.add("header2", "value2");

        // when
        final MultiMap resultHeaders = requestEnricher.enrichHeaders(
                BIDDER_NAME, headers, CaseInsensitiveMultiMap.empty(), BidRequest.builder().build());

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
                .build();

        // when
        final MultiMap resultHeaders = requestEnricher.enrichHeaders(
                BIDDER_NAME, MultiMap.caseInsensitiveMultiMap(), originalHeaders, BidRequest.builder().build());

        // then
        assertThat(resultHeaders.contains("Sec-GPC")).isTrue();
        assertThat(resultHeaders.get("Sec-GPC")).isEqualTo("1");
    }

    @Test
    public void shouldNotOverrideHeadersFromBidRequest() {
        // given
        final CaseInsensitiveMultiMap originalHeaders = CaseInsensitiveMultiMap.builder()
                .add("Sec-GPC", "1")
                .build();
        final MultiMap bidderRequestHeaders = MultiMap.caseInsensitiveMultiMap().add("Sec-GPC", "0");

        // when
        final MultiMap resultHeaders = requestEnricher.enrichHeaders(
                BIDDER_NAME, bidderRequestHeaders, originalHeaders, BidRequest.builder().build());

        // then
        assertThat(resultHeaders.contains("Sec-GPC")).isTrue();
        assertThat(resultHeaders.getAll("Sec-GPC")).hasSize(1);
        assertThat(resultHeaders.get("Sec-GPC")).isEqualTo("0");
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
        final MultiMap resultHeaders = requestEnricher.enrichHeaders(
                BIDDER_NAME, MultiMap.caseInsensitiveMultiMap(), CaseInsensitiveMultiMap.empty(), bidRequest);

        // then
        final MultiMap expectedHeaders = MultiMap.caseInsensitiveMultiMap();
        expectedHeaders.add("x-prebid", "pbjs/4.39,prebid-mobile/1.2.3,pbs-java/1.00");
        assertThat(isEqualsMultiMaps(resultHeaders, expectedHeaders)).isTrue();
    }

    @Test
    public void shouldAddContentEncodingHeaderIfRequiredByBidderConfig() {
        // given
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
                0,
                false,
                false,
                CompressionType.GZIP));

        final CaseInsensitiveMultiMap originalHeaders = CaseInsensitiveMultiMap.builder().build();

        // when
        final MultiMap resultHeaders = requestEnricher.enrichHeaders(
                BIDDER_NAME, MultiMap.caseInsensitiveMultiMap(), originalHeaders, BidRequest.builder().build());

        // then
        assertThat(resultHeaders.contains("Content-Encoding")).isTrue();
        assertThat(resultHeaders.get("Content-Encoding")).isEqualTo("gzip");
    }

    private static boolean isEqualsMultiMaps(MultiMap left, MultiMap right) {
        return left.size() == right.size() && left.entries().stream()
                .allMatch(entry -> right.contains(entry.getKey(), entry.getValue(), true));
    }
}
