package org.prebid.server.auction.categorymapping;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.model.CategoryMappingResult;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.settings.model.Account;

import java.util.List;

public interface CategoryMappingService {

    Future<CategoryMappingResult> createCategoryMapping(List<BidderResponse> bidderResponses,
                                                        BidRequest bidRequest,
                                                        Account account,
                                                        Timeout timeout);
}
