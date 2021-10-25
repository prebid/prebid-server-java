package org.prebid.server.bidder.impactify;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ImpactifyBidderTest extends VertxTest {

    private static final String TEST_ENDPOINT = "https://test.endpoint.com";
    private static final String INCORRECT_TEST_ENDPOINT = "incorrect.endpoint";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private ImpactifyBidder impactifyBidder;

    @Mock
    private CurrencyConversionService currencyConversionService;

    private static Imp givenImpressionWithData() {
        return Imp.builder()
                .
                .build();
    }

    @Before
    public void setUp() {
        impactifyBidder = new ImpactifyBidder(TEST_ENDPOINT, jacksonMapper, currencyConversionService);
    }

    @Test
    public void createBidderWithWrongEndpointShouldThrowException() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ImpactifyBidder(INCORRECT_TEST_ENDPOINT,
                jacksonMapper, currencyConversionService));
    }

    @Test
    public void one() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(Imp.builder().build()))
                .build();

        //when
        Result<List<HttpRequest<BidRequest>>> result = impactifyBidder.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors().size()).isEqualTo(0);
        assertThat(result.getValue().size()).isNotEqualTo(0);
        assertThat(result.getValue().get(0)).isNotNull();
    }
}
