package org.prebid.server.bidder;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.MultiMap;
import io.vertx.core.http.CaseInsensitiveHeaders;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpBidderRequestEnricherTest {

    private HttpBidderRequestEnricher requestEnricher;

    @Before
    public void setUp() {

        requestEnricher = new HttpBidderRequestEnricher("1.00");
    }

    @Test
    public void shouldSendPopulatedPostRequest() {
        // given
        final MultiMap headers = new CaseInsensitiveHeaders();
        headers.add("header1", "value1");
        headers.add("header2", "value2");

        // when
        final MultiMap resultHeaders =
                requestEnricher.enrichHeaders(headers, new CaseInsensitiveHeaders(), BidRequest.builder().build());

        // then
        final MultiMap expectedHeaders = new CaseInsensitiveHeaders();
        expectedHeaders.addAll(headers);
        expectedHeaders.add("x-prebid", "pbs-java/1.00");
        assertThat(resultHeaders).hasSize(3);
        assertThat(isEqualsMultiMaps(resultHeaders, expectedHeaders)).isTrue();
    }

    @Test
    public void shouldAddSecGpcHeaderFromOriginalRequest() {
        // given
        final MultiMap originalHeaders = new CaseInsensitiveHeaders().add("Sec-GPC", "1");

        // when
        final MultiMap resultHeaders =
                requestEnricher.enrichHeaders(new CaseInsensitiveHeaders(),
                        originalHeaders, BidRequest.builder().build());

        // then
        assertThat(resultHeaders.contains("Sec-GPC")).isTrue();
        assertThat(resultHeaders.get("Sec-GPC")).isEqualTo("1");
    }

    @Test
    public void shouldNotOverrideHeadersFromBidRequest() {
        // given
        final MultiMap originalHeaders = new CaseInsensitiveHeaders().add("Sec-GPC", "1");
        final MultiMap bidderRequestHeaders = new CaseInsensitiveHeaders().add("Sec-GPC", "0");

        // when
        final MultiMap resultHeaders = requestEnricher.enrichHeaders(bidderRequestHeaders,
                originalHeaders, BidRequest.builder().build());

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
        final MultiMap resultHeaders = requestEnricher.enrichHeaders(new CaseInsensitiveHeaders(),
                new CaseInsensitiveHeaders(), bidRequest);

        // then
        final MultiMap expectedHeaders = new CaseInsensitiveHeaders();
        expectedHeaders.add("x-prebid", "pbjs/4.39,prebid-mobile/1.2.3,pbs-java/1.00");
        assertThat(isEqualsMultiMaps(resultHeaders, expectedHeaders)).isTrue();
    }

    private static boolean isEqualsMultiMaps(MultiMap left, MultiMap right) {
        return left.size() == right.size() && left.entries().stream()
                .allMatch(entry -> right.contains(entry.getKey(), entry.getValue(), true));
    }
}
