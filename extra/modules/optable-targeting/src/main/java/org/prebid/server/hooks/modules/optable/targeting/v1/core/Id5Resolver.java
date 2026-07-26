package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Uid;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Ortb2;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.User;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Id5Resolver {

    public static final String STR_OPTABLE_CO = "optable.co";
    public static final String STR_ID_5_SYNC_COM = "id5-sync.com";
    public static final String STR_OPTABLE = "optable";
    public static final String STR_REF = "ref";
    public static final String STR_SIGNATURE = "signature";

    private Id5Resolver() {

    }

    public static String resolveId5Signature(TargetingResult targetingResult) {
        if (targetingResult == null) {
            return null;
        }

        final String ref = Optional.of(targetingResult)
                .map(TargetingResult::getOrtb2)
                .map(Ortb2::getUser)
                .map(User::getEids)
                .orElseGet(List::of)
                .stream()
                .filter(it -> STR_OPTABLE_CO.equals(it.getInserter()) && STR_ID_5_SYNC_COM.equals(it.getSource()))
                .map(Eid::getUids)
                .flatMap(Collection::stream)
                .map(Uid::getExt)
                .filter(Objects::nonNull)
                .map(it -> it.get(STR_OPTABLE))
                .filter(Objects::nonNull)
                .map(it -> it.get(STR_REF))
                .filter(Objects::nonNull)
                .map(JsonNode::asText)
                .findFirst()
                .orElse(null);

        if (ref == null) {
            return null;
        }

        return Optional.ofNullable(targetingResult.getRefs())
                .map(refs -> refs.get(ref))
                .map(it -> it.get(STR_SIGNATURE))
                .map(JsonNode::asText)
                .orElse(null);
    }
}
