package org.prebid.server.bidder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.exception.PreBidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class BidderUtilTest extends VertxTest {

    @Test
    public void parseResponseShouldReturnNullWhenStatusCode204() {
        assertThat(BidderUtil.parseResponse(HttpResponse.of(204, null, null))).isNull();
    }

    @Test
    public void parseResponseShouldThrowExceptionWhenStatusCodeIsNot200() {
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> BidderUtil.parseResponse(HttpResponse.of(404, null, null)))
                .withMessage("Unexpected status code: 404. Run with request.test = 1 for more info");
    }

    @Test
    public void parseResponseShouldThrowExceptionWhenResponseBodyCouldNotParsed() {
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> BidderUtil.parseResponse(HttpResponse.of(200, null, "invalid_body")))
                .withMessageStartingWith("Unrecognized token 'invalid_body':");
    }

    @Test
    public void parseResponseShouldReturnBidRequest() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder().id("test").build();
        final HttpResponse response = HttpResponse.of(200, null, mapper.writeValueAsString(bidRequest));

        // when
        final BidResponse result = BidderUtil.parseResponse(response);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("test");
    }
}
