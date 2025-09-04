package org.prebid.server.bidder.alvads;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.json.JacksonMapper;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

class AlvadsBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://helios-ads-qa-core.ssidevops.com/decision/openrtb";
    private JacksonMapper jacksonMapper;
    private AlvadsBidder target;

    @BeforeEach
    void setUp() {
        jacksonMapper = mock(JacksonMapper.class);
        target = new AlvadsBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AlvadsBidder("invalid_url", jacksonMapper));
    }

    @Test
    void makeHttpRequestsShouldReturnErrorForInvalidImpExt() {
        // Mock ObjectMapper
        final ObjectMapper objectMapperMock = mock(ObjectMapper.class);
        when(jacksonMapper.mapper()).thenReturn(objectMapperMock);

        doThrow(new IllegalArgumentException("Invalid imp ext"))
                .when(objectMapperMock)
                .convertValue((Object) any(), (Class<Object>) any());

        final ObjectNode extNode = new ObjectMapper().createObjectNode();
        extNode.put("bidder", "invalid");

        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(Imp.builder().id("1").ext(extNode).build()))
                .build();

        final Result<List<HttpRequest<AlvadsRequestORTB>>> result = target.makeHttpRequests(bidRequest);

        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().get(0).getMessage()).contains("Invalid imp ext");
    }

    @Test
    void makeHttpRequestsShouldReturnHttpRequest() {
        final ObjectMapper objectMapperMock = mock(ObjectMapper.class);
        when(jacksonMapper.mapper()).thenReturn(objectMapperMock);
        when(objectMapperMock.convertValue((Object) any(), (Class<Object>) any()))
                .thenReturn(AlvadsImpExt.of("publisherIdTest", null));

        final ObjectNode impExtNode = new ObjectMapper().createObjectNode();
        impExtNode.put("bidder", "test");

        final Imp imp = Imp.builder()
                .id("1")
                .banner(Banner.builder().w(300).h(250).build())
                .ext(impExtNode)
                .build();

        final Site site = Site.builder()
                .page("https://testpage.com")
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(imp))
                .site(site)
                .build();

        final Result<List<HttpRequest<AlvadsRequestORTB>>> result = target.makeHttpRequests(bidRequest);

        assertThat(result.getValue()).isNotEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void makeBidsShouldReturnEmptyListForEmptyResponse() {
        final BidResponse bidResponse = BidResponse.builder().build();
        final HttpResponse response = HttpResponse.of(
                200,
                MultiMap.caseInsensitiveMultiMap(),
                Arrays.toString(jacksonMapper.encodeToBytes(bidResponse))
        );

        final BidderCall<AlvadsRequestORTB> call = BidderCall.succeededHttp(
                HttpRequest.<AlvadsRequestORTB>builder().payload(null).build(),
                response,
                null
        );

        final Result<List<BidderBid>> result = target.makeBids(call, BidRequest.builder().build());

        assertThat(result.getValue()).isEmpty();
    }

    @Test
    void makeBidsShouldReturnBidderBids() {

        final String impId = "AE_AD_1748977459403";
        final String publisherId = "D7DACCE3-C23D-4AB9-8FE6-9FF41BF32F8F";
        final String endPointUrl = "https://helios-ads-qa-core.ssidevops.com/decision/openrtb";
        final JacksonMapper jacksonMapper = new JacksonMapper(new ObjectMapper());

        // --- Bid real ---
        final Bid bid = Bid.builder()
                .id("bid1")
                .impid(impId)
                .price(BigDecimal.valueOf(1))
                .build();

        final SeatBid seatBid = SeatBid.builder().bid(List.of(bid)).build();
        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(List.of(seatBid))
                .cur("USD")
                .build();

        final HttpResponse response = HttpResponse.of(
                200,
                MultiMap.caseInsensitiveMultiMap(),
                jacksonMapper.encodeToString(bidResponse)
        );

        final ObjectNode extNode = jacksonMapper.mapper()
                .createObjectNode()
                .putObject("bidder")
                .put("publisherUniqueId", publisherId)
                .put("endPointUrl", endPointUrl);

        final Imp imp = Imp.builder()
                .id(impId)
                .banner(Banner.builder().w(320).h(100).build())
                .ext(extNode)
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(imp))
                .build();

        final AlvadsRequestORTB.AlvaAdsImp alvaImp = new AlvadsRequestORTB.AlvaAdsImp();
        alvaImp.setId(impId);
        alvaImp.setBanner(Map.of("w", 320, "h", 100));

        final AlvadsRequestORTB alvadsRequest = new AlvadsRequestORTB();
        alvadsRequest.setImp(List.of(alvaImp));

        final HttpRequest<AlvadsRequestORTB> httpRequest = HttpRequest.<AlvadsRequestORTB>builder()
                .payload(alvadsRequest)
                .build();

        final BidderCall<AlvadsRequestORTB> call = BidderCall.succeededHttp(httpRequest, response, null);

        final AlvadsBidder bidder = new AlvadsBidder(endPointUrl, jacksonMapper);
        final Result<List<BidderBid>> result = bidder.makeBids(call, bidRequest);

        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getBid().getId()).isEqualTo("bid1");
    }
}

