package org.prebid.server.bidder.impactify;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.impactify.ExtImpImpactify;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class ImpactifyBidderTest extends VertxTest {

    private static final String TEST_ENDPOINT = "https://test.endpoint.com";
    private static final String INCORRECT_TEST_ENDPOINT = "incorrect.endpoint";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private ImpactifyBidder impactifyBidder;

    @Mock
    private CurrencyConversionService currencyConversionService;

    @Before
    public void setUp() {
        impactifyBidder = new ImpactifyBidder(TEST_ENDPOINT, jacksonMapper, currencyConversionService);
    }

    @Test
    public void createBidderWithWrongEndpointShouldThrowException() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ImpactifyBidder(INCORRECT_TEST_ENDPOINT,
                jacksonMapper, currencyConversionService));
    }

    private static Imp givenImpressionWithData() {
        return Imp.builder()
                .bidfloorcur("US")
                .bidfloor(BigDecimal.ONE)
                .banner(Banner.builder().build())
                .video(Video.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(
                        ExtImpImpactify.of("appId", "format", "style"), null)))
                .build();
    }

    @Test
    public void one() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenImpressionWithData()))
                .build();

        //when
        Result<List<HttpRequest<BidRequest>>> result = impactifyBidder.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.ONE, "US"));
    }
}
