package org.prebid.server.bidadjustments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidadjustments.model.BidAdjustments;
import org.prebid.server.bidadjustments.model.BidAdjustmentsRule;
import org.prebid.server.bidadjustments.model.BidAdjustmentsRules;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.util.dsl.config.PrebidConfigMatchingStrategy;
import org.prebid.server.util.dsl.config.PrebidConfigParameter;
import org.prebid.server.util.dsl.config.PrebidConfigParameters;
import org.prebid.server.util.dsl.config.PrebidConfigSource;
import org.prebid.server.util.dsl.config.impl.MostAccurateCombinationStrategy;
import org.prebid.server.util.dsl.config.impl.SimpleDirectParameter;
import org.prebid.server.util.dsl.config.impl.SimpleParameters;
import org.prebid.server.util.dsl.config.impl.SimpleSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class BidAdjustmentsRulesResolver {

    public static final String WILDCARD = "*";
    public static final String DELIMITER = "|";

    private final PrebidConfigMatchingStrategy matchingStrategy;
    private final ObjectMapper mapper;

    public BidAdjustmentsRulesResolver(JacksonMapper mapper) {
        this.matchingStrategy = new MostAccurateCombinationStrategy();
        this.mapper = Objects.requireNonNull(mapper).mapper();
    }

    public List<BidAdjustmentsRule> resolve(BidRequest bidRequest,
                                            ImpMediaType targetMediaType,
                                            String targetBidder,
                                            String targetDealId) {

        final BidAdjustmentsRules bidAdjustments = BidAdjustmentsRules.of(extractBidAdjustments(bidRequest));
        return findRules(bidAdjustments, targetMediaType, targetBidder, targetDealId);
    }

    private BidAdjustments extractBidAdjustments(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getExt())
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getBidadjustments)
                .map(node -> mapper.convertValue(node, BidAdjustments.class))
                .orElse(null);
    }

    private List<BidAdjustmentsRule> findRules(BidAdjustmentsRules bidAdjustments,
                                               ImpMediaType targetMediaType,
                                               String targetBidder,
                                               String targetDealId) {

        final Map<String, List<BidAdjustmentsRule>> rules = bidAdjustments.getRules();
        final PrebidConfigSource source = SimpleSource.of(WILDCARD, DELIMITER, rules.keySet());
        final PrebidConfigParameters parameters = createParameters(targetMediaType, targetBidder, targetDealId);

        final String rule = matchingStrategy.match(source, parameters);
        return rule == null ? Collections.emptyList() : rules.get(rule);
    }

    private PrebidConfigParameters createParameters(ImpMediaType mediaType, String bidder, String dealId) {
        final List<PrebidConfigParameter> conditionsMatchers = List.of(
                SimpleDirectParameter.of(mediaType.toString()),
                SimpleDirectParameter.of(bidder),
                StringUtils.isNotBlank(dealId) ? SimpleDirectParameter.of(dealId) : PrebidConfigParameter.wildcard());

        return SimpleParameters.of(conditionsMatchers);
    }
}
