package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import org.prebid.server.floors.model.PriceFloorModelGroup;
import org.prebid.server.floors.model.PriceFloorResult;
import org.prebid.server.proto.openrtb.ext.request.BidAdjustmentMediaType;

public interface PriceFloorResolver {

    PriceFloorResult resolve(BidRequest bidRequest, PriceFloorModelGroup modelGroup,
                             Imp imp, BidAdjustmentMediaType mediaType, Format format, String currency);

    default PriceFloorResult resolve(BidRequest bidRequest, PriceFloorModelGroup modelGroup, Imp imp, String currency) {
        return resolve(bidRequest, modelGroup, imp, null, null, currency);
    }

    static NoOpPriceFloorResolver noOp() {
        return new PriceFloorResolver.NoOpPriceFloorResolver();
    }

    class NoOpPriceFloorResolver implements PriceFloorResolver {

        @Override
        public PriceFloorResult resolve(BidRequest bidRequest,
                                        PriceFloorModelGroup modelGroup,
                                        Imp imp,
                                        BidAdjustmentMediaType mediaType,
                                        Format format,
                                        String currency) {
            return PriceFloorResult.empty();
        }
    }
}
