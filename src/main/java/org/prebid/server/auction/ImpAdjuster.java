package org.prebid.server.auction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Imp;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.validation.ImpValidator;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ImpAdjuster {

    private static final String IMP_EXT = "ext";
    private static final String EXT_PREBID = "prebid";
    private static final String EXT_PREBID_BIDDER = "bidder";
    private static final String EXT_PREBID_IMP = "imp";

    private final ImpValidator impValidator;
    private final JacksonMapper jacksonMapper;
    private final JsonMerger jsonMerger;

    public ImpAdjuster(JacksonMapper jacksonMapper,
                       JsonMerger jsonMerger,
                       ImpValidator impValidator) {
        this.impValidator = Objects.requireNonNull(impValidator);
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
    }

    public Imp adjust(Imp originalImp, String bidder, List<String> debugMessages) {
        final JsonNode impExtPrebidImp = bidderParamsFromImpExtPrebidImp(originalImp.getExt());
        if (impExtPrebidImp == null) {
            return originalImp;
        }

        final JsonNode bidderNode = getBidderNode(bidder, impExtPrebidImp);

        if (bidderNode == null || bidderNode.isEmpty()) {
            return originalImp;
        }

        // remove circular references according to the requirements
        removeExtPrebidBidder(bidderNode);

        try {
            final JsonNode originalImpNode = jacksonMapper.mapper().valueToTree(originalImp);
            final JsonNode mergedImpNode = jsonMerger.merge(bidderNode, originalImpNode);

            // clean up merged imp.ext.prebid.imp
            removeImpExtPrebidImp(mergedImpNode);

            final Imp resultImp = jacksonMapper.mapper().convertValue(mergedImpNode, Imp.class);

            impValidator.validateImp(resultImp);
            return resultImp;
        } catch (Exception e) {
            debugMessages.add("imp.ext.prebid.imp.%s can not be merged into original imp [id=%s], reason: %s"
                    .formatted(bidder, originalImp.getId(), e.getMessage()));
            return originalImp;
        }
    }

    private static JsonNode bidderParamsFromImpExtPrebidImp(ObjectNode ext) {
        return Optional.ofNullable(ext)
                .map(extNode -> extNode.get(EXT_PREBID))
                .map(prebidNode -> prebidNode.get(EXT_PREBID_IMP))
                .orElse(null);
    }

    private static JsonNode getBidderNode(String bidderName, JsonNode node) {
        final Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            final String fieldName = fieldNames.next();
            if (fieldName.equalsIgnoreCase(bidderName)) {
                return node.get(fieldName);
            }
        }
        return null;
    }

    private static void removeExtPrebidBidder(JsonNode bidderNode) {
        Optional.ofNullable(bidderNode.get(IMP_EXT))
                .map(extNode -> extNode.get(EXT_PREBID))
                .map(ObjectNode.class::cast)
                .ifPresent(ext -> ext.remove(EXT_PREBID_BIDDER));
    }

    private static void removeImpExtPrebidImp(JsonNode mergedImpNode) {
        Optional.ofNullable(mergedImpNode.get(IMP_EXT))
                .map(extNode -> extNode.get(EXT_PREBID))
                .map(ObjectNode.class::cast)
                .ifPresent(prebid -> prebid.remove(EXT_PREBID_IMP));
    }
}
