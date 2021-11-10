package org.prebid.server.log;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class CriteriaLogManager {

    private static final Logger logger = LoggerFactory.getLogger(CriteriaLogManager.class);

    private final Set<Criteria> criterias = new ConcurrentHashSet<>();

    private final JacksonMapper mapper;

    public CriteriaLogManager(JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public void log(Logger logger, Criteria criteria, Object message, Consumer<Object> defaultLogger) {
        if (criterias.isEmpty()) {
            defaultLogger.accept(message);
        }
        criterias.forEach(cr -> cr.log(criteria, logger, message, defaultLogger));
    }

    public void log(Logger logger, String account, Object message, Consumer<Object> defaultLogger) {
        log(logger, Criteria.builder().account(account).build(), message, defaultLogger);
    }

    public void log(Logger logger, String account, String bidder, String lineItemId, Object message,
                    Consumer<Object> defaultLogger) {
        log(logger, Criteria.builder().account(account).bidder(bidder).lineItemId(lineItemId).build(),
                message, defaultLogger);
    }

    public BidResponse traceResponse(Logger logger, BidResponse bidResponse, BidRequest bidRequest,
                                     boolean debugEnabled) {
        if (criterias.isEmpty()) {
            return bidResponse;
        }

        final String jsonBidResponse;
        final String jsonBidRequest;
        try {
            jsonBidResponse = mapper.encodeToString(bidResponse);
            jsonBidRequest = debugEnabled ? null : mapper.encodeToString(bidRequest);
        } catch (EncodeException e) {
            CriteriaLogManager.logger.warn("Failed to parse bidResponse or bidRequest to json string: {0}", e);
            return bidResponse;
        }

        if (debugEnabled) {
            criterias.forEach(criteria -> criteria.logResponse(jsonBidResponse, logger));
        } else {
            criterias.forEach(criteria -> criteria.logResponseAndRequest(jsonBidResponse, jsonBidRequest, logger));
        }

        return bidResponse;
    }

    public void removeCriteria(Criteria criteria) {
        criterias.remove(criteria);
    }

    public void addCriteria(Criteria criteria) {
        criterias.add(criteria);
    }

    public void removeAllCriteria() {
        criterias.clear();
    }
}
