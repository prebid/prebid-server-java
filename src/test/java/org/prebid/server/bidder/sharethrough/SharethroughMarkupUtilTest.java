package org.prebid.server.bidder.sharethrough;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.sharethrough.model.StrUriParameters;
import org.prebid.server.bidder.sharethrough.model.bidResponse.ExtImpSharethroughResponse;

import static org.assertj.core.api.Assertions.assertThat;

public class SharethroughMarkupUtilTest extends VertxTest {

    @Test
    public void getAdMarkupShouldReturnScriptWithParametersFromImpResponseAndUriParameters() throws JsonProcessingException {
        // given
        final ExtImpSharethroughResponse impResponse = ExtImpSharethroughResponse.builder()
                .bidId("bid")
                .adserverRequestId("arid")
                .build();

        final StrUriParameters uriParameters = StrUriParameters.builder()
                .pkey("pkey")
                .build();

        // when
        final String result = SharethroughMarkupUtil.getAdMarkup(impResponse, uriParameters);

        // then
        final String expected = "<img src=\"//b.sharethrough.com/butler?type=s2s-win&arid=arid\" />\n" +
                "\t\t<div data-str-native-key=\"pkey\" data-stx-response-name=\"str_response_bid\"></div>\n" +
                // Encoded {"adserverRequestId":"arid","bidId":"bid"}
                "\t\t<script>var str_response_bid = \"eyJhZHNlcnZlclJlcXVlc3RJZCI6ImFyaWQiLCJiaWRJZCI6ImJpZCJ9\"</script>";

        assertThat(result.contains(expected)).isTrue();
    }

    @Test
    public void getAdMarkupShouldContainsIframeScriptWhenIframeItTrue() throws JsonProcessingException {
        // given
        final ExtImpSharethroughResponse impResponse = ExtImpSharethroughResponse.builder()
                .bidId("bid")
                .adserverRequestId("arid")
                .build();

        final StrUriParameters uriParameters = StrUriParameters.builder()
                .pkey("pkey")
                .iframe(true)
                .build();

        // when
        final String result = SharethroughMarkupUtil.getAdMarkup(impResponse, uriParameters);

        // then
        final String expectedContains = "<script src=\"//native.sharethrough.com/assets/sfp.js\"></script>\n";
        assertThat(result.contains(expectedContains)).isTrue();
    }

    @Test
    public void getAdMarkupShouldContainsScriptWhenIframeItFalse() throws JsonProcessingException {
        // given
        final ExtImpSharethroughResponse impResponse = ExtImpSharethroughResponse.builder()
                .bidId("bid")
                .adserverRequestId("arid")
                .build();

        final StrUriParameters uriParameters = StrUriParameters.builder()
                .pkey("pkey")
                .build();

        // when
        final String result = SharethroughMarkupUtil.getAdMarkup(impResponse, uriParameters);

        // then
        final String expectedContains = "<script src=\"//native.sharethrough.com/assets/sfp-set-targeting.js\"></script>";
        assertThat(result.contains(expectedContains)).isTrue();
    }
}

