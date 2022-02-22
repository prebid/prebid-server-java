package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Source;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchain;
import org.prebid.server.proto.openrtb.ext.request.ExtSourceSchain;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchainSchainNode;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SchainResolver {

    private static final Logger logger = LoggerFactory.getLogger(SchainResolver.class);

    private final ExtRequestPrebidSchainSchainNode globalNode;

    private SchainResolver(ExtRequestPrebidSchainSchainNode globalNode) {
        this.globalNode = globalNode;
    }

    public static SchainResolver create(String globalNodeString, JacksonMapper mapper) {
        return new SchainResolver(globalNodeOrNull(globalNodeString, Objects.requireNonNull(mapper)));
    }

    public ExtSourceSchain resolveForBidder(String bidder, BidRequest bidRequest) {
        final ExtRequest requestExt = bidRequest.getExt();
        final ExtRequestPrebid prebid = requestExt == null ? null : requestExt.getPrebid();
        final List<ExtRequestPrebidSchain> schains = prebid == null ? null : prebid.getSchains();

        ExtSourceSchain bidderSchain = null;
        ExtSourceSchain catchAllSchain = null;
        for (final ExtRequestPrebidSchain schain : ListUtils.emptyIfNull(schains)) {
            catchAllSchain = existingSchainOrNull("*", catchAllSchain, schain);
            bidderSchain = existingSchainOrNull(bidder, bidderSchain, schain);
        }

        return enrich(ObjectUtils.defaultIfNull(bidderSchain, catchAllSchain), bidRequest);
    }

    private static ExtRequestPrebidSchainSchainNode globalNodeOrNull(String globalNodeString, JacksonMapper mapper) {
        if (StringUtils.isBlank(globalNodeString)) {
            return null;
        }

        try {
            return mapper.decodeValue(globalNodeString, ExtRequestPrebidSchainSchainNode.class);
        } catch (DecodeException e) {
            throw new IllegalArgumentException("Exception occurred while parsing global schain node", e);
        }
    }

    private ExtSourceSchain existingSchainOrNull(String bidder,
                                                 ExtSourceSchain existingSchain,
                                                 ExtRequestPrebidSchain schainEntry) {

        if (schainEntry == null
                || CollectionUtils.isEmpty(schainEntry.getBidders())
                || !schainEntry.getBidders().contains(bidder)) {

            return existingSchain;
        }

        if (existingSchain != null) {
            logger.debug("Schain bidder {0} is rejected since it was defined more than once", bidder);
            return null;
        }

        return schainEntry.getSchain();
    }

    private ExtSourceSchain enrich(ExtSourceSchain bidderSpecificSchain, BidRequest bidRequest) {

        if (globalNode == null) {
            return bidderSpecificSchain;
        }

        if (bidderSpecificSchain != null) {
            return enrichSchainWithGlobalNode(bidderSpecificSchain);
        }

        final ExtSourceSchain requestSchain = requestSchain(bidRequest);
        if (requestSchain != null) {
            return enrichSchainWithGlobalNode(requestSchain);
        }

        return newSchainWithGlobalNode();
    }

    private ExtSourceSchain requestSchain(BidRequest bidRequest) {
        final Source source = bidRequest.getSource();
        final ExtSource extSource = source != null ? source.getExt() : null;

        return extSource != null ? extSource.getSchain() : null;
    }

    private ExtSourceSchain enrichSchainWithGlobalNode(ExtSourceSchain requestSchain) {
        return ExtSourceSchain.of(
                requestSchain.getVer(),
                requestSchain.getComplete(),
                appendGlobalNode(requestSchain.getNodes()),
                requestSchain.getExt());
    }

    private List<ExtRequestPrebidSchainSchainNode> appendGlobalNode(List<ExtRequestPrebidSchainSchainNode> nodes) {
        final ArrayList<ExtRequestPrebidSchainSchainNode> updatedNodes = new ArrayList<>(ListUtils.emptyIfNull(nodes));
        updatedNodes.add(globalNode);

        return updatedNodes;
    }

    private ExtSourceSchain newSchainWithGlobalNode() {
        return ExtSourceSchain.of(null, null, Collections.singletonList(globalNode), null);
    }
}
