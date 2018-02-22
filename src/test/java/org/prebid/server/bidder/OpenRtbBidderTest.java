package org.prebid.server.bidder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.model.openrtb.ext.response.BidType;

import java.util.Arrays;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.*;

public class OpenRtbBidderTest extends VertxTest {

    @Test
    public void bidTypeShouldReturnBannerBidTypeByDefaultWhenImpIdToBidTypeEmpty() {
        // when
        final BidType result = OpenRtbBidder.bidType(Bid.builder().impid("abc").build(), emptyMap());

        // then
        assertThat(result).isEqualTo(BidType.banner);
    }

    @Test
    public void bidTypeShouldReturnBidTypeWhenImpIdToBidTypeContainsBidType() {
        // when
        final BidType result = OpenRtbBidder.bidType(Bid.builder().impid("abc").build(),
                singletonMap("abc", BidType.video));

        // then
        assertThat(result).isEqualTo(BidType.video);
    }

    @Test
    public void impidToBidTypeShouldReturnImpIdsMappedToVideoOrBannerBidTypes() {
        // when
        final Map<String, BidType> result = OpenRtbBidder.impidToBidType(BidRequest.builder().imp(
                Arrays.asList(
                        Imp.builder().id("123").video(Video.builder().build()).build(),
                        Imp.builder().id("456").build()))
                .build());

        // then
        assertThat(result)
                .containsEntry("123", BidType.video)
                .containsEntry("456", BidType.banner)
                .hasSize(2);
    }

    @Test
    public void parseResponseShouldReturnNullWhenStatusCode204() {
        assertThat(OpenRtbBidder.parseResponse(HttpResponse.of(204, null, null))).isNull();
    }

    @Test
    public void parseResponseShouldThrowExceptionWhenStatusCodeIsNot200() {
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> OpenRtbBidder.parseResponse(HttpResponse.of(404, null, null)))
                .withMessage("Unexpected status code: 404. Run with request.test = 1 for more info");
    }

    @Test
    public void parseResponseShouldThrowExceptionWhenResponseBodyCouldNotParsed() {
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> OpenRtbBidder.parseResponse(HttpResponse.of(200, null, "invalid_body")))
                .withMessageStartingWith("Unrecognized token 'invalid_body':");
    }

    @Test
    public void parseResponseShouldReturnBidRequest() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder().id("test").build();
        final HttpResponse response = HttpResponse.of(200, null, mapper.writeValueAsString(bidRequest));

        // when
        final BidResponse result = OpenRtbBidder.parseResponse(response);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("test");
    }

    @Test
    public void validateUrlShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> OpenRtbBidder.validateUrl(null));
    }

    @Test
    public void validateUrlShouldFailOnInvalidUrl() {
        // when and then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OpenRtbBidder.validateUrl("invalid_url"))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void validateUrlShouldReturnExpectedUrl() {
        // when
        final String url = OpenRtbBidder.validateUrl("http://domain.org/query-string?a=1");

        // then
        assertThat(url).isNotNull();
        assertThat(url).isEqualTo("http://domain.org/query-string?a=1");
    }
}