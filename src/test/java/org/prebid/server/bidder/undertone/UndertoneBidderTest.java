package org.prebid.server.bidder.undertone;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.undertone.proto.UndertoneRequestExt;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.undertone.ExtImpUndertone;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class UndertoneBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://test.undertone.com";

    private UndertoneBidder undertoneBidder;

    @Before
    public void setUp() {
        undertoneBidder = new UndertoneBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new UndertoneBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void errorOnNoAppSite() {
        final BidRequest bidRequest = BidRequest.builder()
                .build();
        final Result<List<HttpRequest<BidRequest>>> result = undertoneBidder.makeHttpRequests(bidRequest);

        assertThat(result.getErrors()).hasSize(1);
        final BidderError bidderError = result.getErrors().get(0);

        assertThat(bidderError.getMessage()).isEqualTo("invalid bidRequest: no App/Site objects");
        assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input);
    }

    @Test
    public void errorOnNoPublisherId() {
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.site(Site.builder()
                        .id("site-id")
                        .build()),
                impBuilder -> impBuilder
                        .id("imp-id")
                        .banner(Banner.builder()
                                .id("banner-id")
                                .w(300)
                                .h(600)
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpUndertone.of(null, 12345)))));

        final Result<List<HttpRequest<BidRequest>>> result = undertoneBidder.makeHttpRequests(bidRequest);
        assertThat(result.getErrors()).hasSize(1);
        final BidderError bidderError = result.getErrors().get(0);

        assertThat(bidderError.getMessage()).isEqualTo("invalid bidRequest: no publisher-id");
        assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input);
    }

    @Test
    public void errorOnInvalidImps() {
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.site(Site.builder()
                        .id("site-id")
                        .build()),
                impBuilder -> impBuilder
                        .id("imp-id")
                        .banner(Banner.builder()
                                .id("banner-id")
                                .w(300)
                                .h(600)
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpUndertone.of(1234, null)))));

        final Result<List<HttpRequest<BidRequest>>> result = undertoneBidder.makeHttpRequests(bidRequest);

        assertThat(result.getErrors()).hasSize(1);
        final BidderError bidderError = result.getErrors().get(0);

        assertThat(bidderError.getMessage()).isEqualTo("invalid bidRequest: no valid imps");
        assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input);
    }

    @Test
    public void testValidBidRequest() {
        final Site givenSite = Site.builder()
                .id("site-id")
                .build();

        final Imp.ImpBuilder givenImpBuilder = Imp.builder()
                .id("imp-id")
                .banner(Banner.builder()
                        .id("banner-id")
                        .w(300)
                        .h(600)
                        .build())
                .ext(mapper.valueToTree(
                        ExtPrebid.of(null, ExtImpUndertone.of(1234, 12345))));

        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                        .id("req-id")
                        .site(givenSite),
                impBuilder -> givenImpBuilder);

        final Result<List<HttpRequest<BidRequest>>> result = undertoneBidder.makeHttpRequests(bidRequest);
        assertThat(result.getErrors()).isEmpty();

        final ExtRequest expectedExt = jacksonMapper.fillExtension(ExtRequest.empty(),
                UndertoneRequestExt.of(3, "1.0.0"));

        final Publisher expectedPublisher = Publisher.builder()
                .id(String.valueOf(1234))
                .build();

        final Site expectedSite = givenSite.toBuilder()
                .publisher(expectedPublisher)
                .build();

        final Imp expectedImp = givenImpBuilder
                .tagid(String.valueOf(12345))
                .ext(null)
                .build();

        final BidRequest expectedBidRequest = BidRequest.builder()
                .id("req-id")
                .site(expectedSite)
                .imp(List.of(expectedImp))
                .ext(expectedExt)
                .build();

        assertThat(result.getValue())
                .hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsExactly(expectedBidRequest);
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder())
                .build();
    }

}

