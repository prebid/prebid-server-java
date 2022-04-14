package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import org.prebid.server.floors.model.PriceFloorModelGroup;
import org.prebid.server.floors.model.PriceFloorResult;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;

import java.util.List;

public interface PriceFloorResolver {

    PriceFloorResult resolve(BidRequest bidRequest,
                             PriceFloorModelGroup modelGroup,
                             Imp imp,
                             ImpMediaType mediaType,
                             Format format,
                             List<String> warnings);

    default PriceFloorResult resolve(BidRequest bidRequest,
                                     PriceFloorModelGroup modelGroup,
                                     Imp imp,
                                     List<String> warnings) {

        return resolve(bidRequest, modelGroup, imp, null, null, warnings);
    }

    static NoOpPriceFloorResolver noOp() {
        return new NoOpPriceFloorResolver();
    }

    class NoOpPriceFloorResolver implements PriceFloorResolver {

        @Override
        public PriceFloorResult resolve(BidRequest bidRequest,
                                        PriceFloorModelGroup modelGroup,
                                        Imp imp,
                                        ImpMediaType mediaType,
                                        Format format,
                                        List<String> warnings) {

            return null;
        }
    }
}
