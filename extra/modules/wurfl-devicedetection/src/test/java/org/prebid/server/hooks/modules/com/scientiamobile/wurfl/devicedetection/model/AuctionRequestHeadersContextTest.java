package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.model;

import org.junit.jupiter.api.Test;
import org.prebid.server.model.CaseInsensitiveMultiMap;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionRequestHeadersContextTest {

    @Test
    void fromShouldHandleNullHeaders() {
        // when
        final AuctionRequestHeadersContext result = AuctionRequestHeadersContext.from(null);

        // then
        assertThat(result.getHeaders()).isEmpty();
    }

    @Test
    void fromShouldConvertCaseInsensitiveMultiMapToHeaders() {
        // given
        final CaseInsensitiveMultiMap multiMap = CaseInsensitiveMultiMap.builder()
                .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Test")
                .add("Header2", "value2")
                .build();

        // when
        final AuctionRequestHeadersContext target = AuctionRequestHeadersContext.from(multiMap);

        // then
        assertThat(target.getHeaders())
                .hasSize(2)
                .containsEntry("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Test")
                .containsEntry("Header2", "value2");
    }

    @Test
    void fromShouldTakeFirstValueForDuplicateHeaders() {
        // given
        final CaseInsensitiveMultiMap multiMap = CaseInsensitiveMultiMap.builder()
                .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Test")
                .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Test2")
                .build();

        // when
        final AuctionRequestHeadersContext target = AuctionRequestHeadersContext.from(multiMap);

        // then
        assertThat(target.getHeaders())
                .hasSize(1)
                .containsEntry("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Test");
    }

    @Test
    void fromShouldHandleEmptyMultiMap() {
        // given
        final CaseInsensitiveMultiMap emptyMultiMap = CaseInsensitiveMultiMap.empty();

        // when
        final AuctionRequestHeadersContext target = AuctionRequestHeadersContext.from(emptyMultiMap);

        // then
        assertThat(target.getHeaders()).isEmpty();
    }
}
