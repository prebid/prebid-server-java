package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.request.SupplyChainNode;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class SupplyChainResolver {

    private static final Logger logger = LoggerFactory.getLogger(SupplyChainResolver.class);

    private final SupplyChainNode globalNode;

    private SupplyChainResolver(SupplyChainNode globalNode) {
        this.globalNode = globalNode;
    }

    public static SupplyChainResolver create(String globalNodeString, JacksonMapper mapper) {
        return new SupplyChainResolver(globalNodeOrNull(globalNodeString, Objects.requireNonNull(mapper)));
    }

    public SupplyChain resolveForBidder(String bidder, BidRequest bidRequest) {
        final ExtRequest requestExt = bidRequest.getExt();
        final ExtRequestPrebid prebid = requestExt == null ? null : requestExt.getPrebid();
        final List<ExtRequestPrebidSchain> schains = prebid == null ? null : prebid.getSchains();

        SupplyChain bidderSchain = null;
        SupplyChain catchAllSchain = null;
        for (ExtRequestPrebidSchain schain : ListUtils.emptyIfNull(schains)) {
            catchAllSchain = existingSchainOrNull("*", catchAllSchain, schain);
            bidderSchain = existingSchainOrNull(bidder, bidderSchain, schain);
        }

        return enrich(ObjectUtils.defaultIfNull(bidderSchain, catchAllSchain), bidRequest);
    }

    private static SupplyChainNode globalNodeOrNull(String globalNodeString, JacksonMapper mapper) {
        if (StringUtils.isBlank(globalNodeString)) {
            return null;
        }

        try {
            return mapper.decodeValue(globalNodeString, SupplyChainNode.class);
        } catch (DecodeException e) {
            throw new IllegalArgumentException("Exception occurred while parsing global schain node", e);
        }
    }

    private SupplyChain existingSchainOrNull(String bidder,
                                             SupplyChain existingSchain,
                                             ExtRequestPrebidSchain schainEntry) {

        if (!containsBidder(schainEntry, bidder)) {
            return existingSchain;
        }

        if (existingSchain != null) {
            logger.debug("Schain bidder {} is rejected since it was defined more than once", bidder);
            return null;
        }

        return schainEntry.getSchain();
    }

    private static boolean containsBidder(ExtRequestPrebidSchain schainEntry, String bidder) {
        return Stream.ofNullable(schainEntry)
                .map(ExtRequestPrebidSchain::getBidders)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .anyMatch(schainEntryBidder -> StringUtils.equalsIgnoreCase(schainEntryBidder, bidder));
    }

    private SupplyChain enrich(SupplyChain bidderSpecificSchain, BidRequest bidRequest) {
        if (globalNode == null) {
            return bidderSpecificSchain;
        }

        if (bidderSpecificSchain != null) {
            return enrichSchainWithGlobalNode(bidderSpecificSchain);
        }

        final SupplyChain requestSchain = requestSchain(bidRequest);
        if (requestSchain != null) {
            return enrichSchainWithGlobalNode(requestSchain);
        }

        return newSchainWithGlobalNode();
    }

    private SupplyChain requestSchain(BidRequest bidRequest) {
        final Source source = bidRequest.getSource();
        return source != null ? source.getSchain() : null;
    }

    private SupplyChain enrichSchainWithGlobalNode(SupplyChain requestSchain) {
        return SupplyChain.of(
                requestSchain.getComplete(),
                appendGlobalNode(requestSchain.getNodes()),
                requestSchain.getVer(),
                requestSchain.getExt());
    }

    private List<SupplyChainNode> appendGlobalNode(List<SupplyChainNode> nodes) {
        final ArrayList<SupplyChainNode> updatedNodes = new ArrayList<>(ListUtils.emptyIfNull(nodes));
        updatedNodes.add(globalNode);

        return updatedNodes;
    }

    private SupplyChain newSchainWithGlobalNode() {
        return SupplyChain.of(null, Collections.singletonList(globalNode), null, null);
    }
}
