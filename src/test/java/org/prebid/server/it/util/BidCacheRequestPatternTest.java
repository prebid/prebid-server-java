package org.prebid.server.it.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import org.junit.Test;
import org.prebid.server.json.ObjectMapperProvider;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class BidCacheRequestPatternTest {

    private final ObjectMapper mapper = ObjectMapperProvider.mapper();

    @Test
    public void shouldMatchWhenPutsAreUnordered() throws IOException {
        // given
        final String original = jsonFrom("bidcacherequestpattern/original-bid-request.json");
        final String unordered = jsonFrom("bidcacherequestpattern/unordered-bid-request.json");

        // when
        final MatchResult result = new BidCacheRequestPattern(original).match(unordered);

        // then
        assertThat(result.getDistance()).isEqualTo(0);
        assertThat(result.isExactMatch()).isTrue();
    }

    @Test
    public void shouldNotMatchWhenPutIsMissing() throws IOException {
        // given
        final String original = jsonFrom("bidcacherequestpattern/original-bid-request.json");
        final String missing = jsonFrom("bidcacherequestpattern/missing-put-bid-request.json");

        // when
        final MatchResult result = new BidCacheRequestPattern(original).match(missing);

        // then
        assertThat(result.getDistance()).isEqualTo(1);
        assertThat(result.isExactMatch()).isFalse();
    }

    @Test
    public void shouldNotMatchWhenPutIsRedundant() throws IOException {
        // given
        final String original = jsonFrom("bidcacherequestpattern/original-bid-request.json");
        final String redundant = jsonFrom("bidcacherequestpattern/redundant-put-bid-request.json");

        // when
        final MatchResult result = new BidCacheRequestPattern(original).match(redundant);

        // then
        assertThat(result.getDistance()).isEqualTo(1);
        assertThat(result.isExactMatch()).isFalse();
    }

    @Test
    public void shouldNotMatchWhenPutIsChanged() throws IOException {
        // given
        final String original = jsonFrom("bidcacherequestpattern/original-bid-request.json");
        final String changed = jsonFrom("bidcacherequestpattern/changed-put-bid-request.json");

        // when
        final MatchResult result = new BidCacheRequestPattern(original).match(changed);

        // then
        assertThat(result.getDistance()).isEqualTo(3);
        assertThat(result.isExactMatch()).isFalse();
    }

    private String jsonFrom(String file) throws IOException {
        return mapper.writeValueAsString(mapper.readTree(BidCacheRequestPatternTest.class.getResourceAsStream(file)));
    }
}
