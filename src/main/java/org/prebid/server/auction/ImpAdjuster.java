package org.prebid.server.auction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Imp;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.util.StreamUtil;
import org.prebid.server.validation.ImpValidator;

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

    public Imp adjust(Imp originalImp, String bidder, BidderAliases bidderAliases, List<String> debugMessages) {
        final JsonNode impExtPrebidImp = bidderParamsFromImpExtPrebidImp(originalImp.getExt());
        if (impExtPrebidImp == null) {
            return originalImp;
        }

        final JsonNode bidderNode = getBidderNode(bidder, bidderAliases, impExtPrebidImp, debugMessages);

        if (bidderNode == null || bidderNode.isEmpty()) {
            removeImpExtPrebidImp(originalImp.getExt());
            return originalImp;
        }

        removeExtPrebidBidder(bidderNode);

        try {
            final JsonNode originalImpNode = jacksonMapper.mapper().valueToTree(originalImp);
            final JsonNode mergedImpNode = jsonMerger.merge(bidderNode, originalImpNode);

            removeImpExtPrebidImp(mergedImpNode.get(IMP_EXT));

            final Imp resultImp = jacksonMapper.mapper().convertValue(mergedImpNode, Imp.class);

            impValidator.validateImp(resultImp);
            return resultImp;
        } catch (Exception e) {
            debugMessages.add("imp.ext.prebid.imp.%s can not be merged into original imp [id=%s], reason: %s"
                    .formatted(bidder, originalImp.getId(), e.getMessage()));
            removeImpExtPrebidImp(originalImp.getExt());
            return originalImp;
        }
    }

    private static JsonNode bidderParamsFromImpExtPrebidImp(ObjectNode ext) {
        return Optional.ofNullable(ext)
                .map(extNode -> extNode.get(EXT_PREBID))
                .map(prebidNode -> prebidNode.get(EXT_PREBID_IMP))
                .orElse(null);
    }

    private static JsonNode getBidderNode(String bidderName,
                                          BidderAliases bidderAliases,
                                          JsonNode node,
                                          List<String> debugMessages) {

        return StreamUtil.asStream(node.fieldNames())
                .filter(fieldName -> {
                    if (bidderAliases.isSame(fieldName, bidderName)) {
                        return true;
                    } else {
                        debugMessages.add(String.format("imp.ext.prebid.imp.%s is not applicable for %s bidder",
                                fieldName, bidderName));
                        return false;
                    }
                })
                .findFirst()
                .map(node::get)
                .orElse(null);
    }

    private static void removeExtPrebidBidder(JsonNode bidderNode) {
        Optional.ofNullable(bidderNode.get(IMP_EXT))
                .map(extNode -> extNode.get(EXT_PREBID))
                .map(ObjectNode.class::cast)
                .ifPresent(ext -> ext.remove(EXT_PREBID_BIDDER));
    }

    private static void removeImpExtPrebidImp(JsonNode impExp) {
        Optional.ofNullable(impExp.get(EXT_PREBID))
                .map(ObjectNode.class::cast)
                .ifPresent(prebid -> prebid.remove(EXT_PREBID_IMP));
    }
}
