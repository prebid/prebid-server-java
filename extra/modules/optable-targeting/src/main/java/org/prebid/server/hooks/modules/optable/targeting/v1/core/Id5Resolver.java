package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Uid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Ortb2;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.User;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Id5Resolver {

    public static final String OPTABLE_INSERTER = "optable.co";
    public static final String ID5_SOURCE = "id5-sync.com";
    public static final String OPTABLE = "optable";
    public static final String REF = "ref";
    public static final String SIGNATURE = "signature";

    private Id5Resolver() {
    }

    public static String resolveId5Signature(TargetingResult targetingResult) {
        if (targetingResult == null) {
            return null;
        }

        final List<String> refs = Optional.of(targetingResult)
                .map(TargetingResult::getOrtb2)
                .map(Ortb2::getUser)
                .map(User::getEids)
                .orElseGet(List::of)
                .stream()
                .filter(it -> OPTABLE_INSERTER.equals(it.getInserter()) && ID5_SOURCE.equals(it.getSource()))
                .map(Eid::getUids)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(Uid::getExt)
                .filter(Objects::nonNull)
                .map(it -> it.get(OPTABLE))
                .filter(Objects::nonNull)
                .map(it -> it.get(REF))
                .filter(Objects::nonNull)
                .map(JsonNode::asText)
                .toList();

        if (CollectionUtils.isEmpty(refs)) {
            return null;
        }

        final ObjectNode references = targetingResult.getRefs();
        if (references == null) {
            return null;
        }

        return refs.stream()
                .map(references::get)
                .filter(Objects::nonNull)
                .map(it -> it.get(SIGNATURE))
                .filter(Objects::nonNull)
                .filter(JsonNode::isValueNode)
                .filter(it -> !it.isNull())
                .map(JsonNode::asText)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }
}
