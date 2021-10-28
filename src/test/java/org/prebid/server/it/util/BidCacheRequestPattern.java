package org.prebid.server.it.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.matching.EqualToJsonPattern;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.cache.proto.request.BidCacheRequest;
import org.prebid.server.cache.proto.request.PutObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
                    final List<PutObject> expectedPuts = updateKeysWithUuid(actualPuts, expected.getPuts());
                    // update actual from expected
                    if (CollectionUtils.isEqualCollection(actualPuts, expectedPuts)) {
                        return 0;
                    }

                    return CollectionUtils.disjunction(actualPuts, expectedPuts).size();
                }

                private List<PutObject> updateKeysWithUuid(List<PutObject> actual, List<PutObject> expected) {
                    final List<PutObject> requiredUuidKeyUpdate = expected.stream()
                            .filter(putObject -> putObject.getKey() != null && putObject.getKey().endsWith("{{uuid}}"))
                            .collect(Collectors.toList());

                    final List<PutObject> result =
                            new ArrayList<>(CollectionUtils.disjunction(expected, requiredUuidKeyUpdate));

                    result.addAll(requiredUuidKeyUpdate.stream()
                            .map(requiredUuidKeyPutObject -> updatedWithKey(requiredUuidKeyPutObject, actual))
                            .collect(Collectors.toList()));
                    return result;
                }

                private PutObject updatedWithKey(PutObject requiredUuidKeyPutObject,
                                                 List<PutObject> actual) {
                    final PutObject correspondedObject = findCorrespondedByValue(requiredUuidKeyPutObject, actual);
                    if (correspondedObject == null || correspondedObject.getKey() == null) {
                        return requiredUuidKeyPutObject;
                    }
                    final String correspondedObjectKey = correspondedObject.getKey();
                    final String[] splittedKey = correspondedObjectKey.split("_");
                    final String uuid = getValidUuid(splittedKey[splittedKey.length - 1]);
                    final String updatedKey = uuid != null
                            ? requiredUuidKeyPutObject.getKey().replace("{{uuid}}", uuid)
                            : "";
                    return requiredUuidKeyPutObject.toBuilder().key(updatedKey).build();
                }

                private PutObject findCorrespondedByValue(PutObject searchingObject, List<PutObject> allObjects) {
                    return allObjects.stream()
                            .filter(putObject -> putObject.getValue().equals(searchingObject.getValue()))
                            .findFirst()
                            .orElse(null);
                }

                private String getValidUuid(String uuid) {
                    try {
                        return UUID.fromString(uuid).toString();
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                }
            };

        } catch (Exception e) {
            return MatchResult.noMatch();
        }
    }
}
