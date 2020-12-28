package org.prebid.server.it.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.matching.EqualToJsonPattern;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.cache.proto.request.BidCacheRequest;
import org.prebid.server.cache.proto.request.PutObject;

import java.util.List;

/**
 * Class was created to compare complex Json object when ordering of inner array is not predetermined.
 * The Wiremock equalToJson method that creates {@link EqualToJsonPattern} cannot compare such objects correctly
 * even when using the unordered flag..
 */
public class BidCacheRequestPattern extends StringValuePattern {

    private final BidCacheRequest expected;

    public BidCacheRequestPattern(@JsonProperty("equalToJson") String json) {
        super(json);
        expected = Json.read(json, BidCacheRequest.class);
    }

    @Override
    public String getExpected() {
        return Json.prettyPrint(getValue());
    }

    @Override
    public MatchResult match(String value) {
        try {
            final BidCacheRequest actual = Json.read(value, BidCacheRequest.class);

            return new MatchResult() {
                @Override
                public boolean isExactMatch() {
                    return getDistance() == 0;
                }

                @Override
                public double getDistance() {
                    final List<PutObject> actualPuts = actual.getPuts();
                    final List<PutObject> expectedPuts = expected.getPuts();
                    if (CollectionUtils.isEqualCollection(actualPuts, expectedPuts)) {
                        return 0;
                    }

                    return CollectionUtils.disjunction(actualPuts, expectedPuts).size();
                }
            };
        } catch (Exception e) {
            return MatchResult.noMatch();
        }
    }
}
