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
                .filter(it -> "optable.co".equals(it.getInserter()) && "id5-sync.com".equals(it.getSource()))
                .map(Eid::getUids)
                .flatMap(Collection::stream)
                .map(Uid::getExt)
                .map(it -> it.at("/optable/ref"))
                .filter(Objects::nonNull)
                .map(JsonNode::asText)
                .findFirst()
                .orElse(null);

        if (ref == null) {
            return null;
        }

        return Optional.ofNullable(targetingResult.getRefs())
                .map(refs -> refs.get(ref))
                .map(it -> it.get("signature"))
                .map(JsonNode::asText)
                .orElse(null);
    }
}
