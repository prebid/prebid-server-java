package org.prebid.server.bidder.huaweiads;

import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.huaweiads.model.request.CellInfo;
import org.prebid.server.bidder.huaweiads.model.request.Network;

import java.util.ArrayList;
import java.util.List;

public class HuaweiNetworkBuilder {

    private static final int DEFAULT_UNKNOWN_NETWORK_TYPE = 0;

    public Network build(com.iab.openrtb.request.Device device) {
        if (device == null) {
            return null;
        }

        final List<CellInfo> cellInfos = new ArrayList<>();
        Integer carrier = null;

        if (StringUtils.isNotBlank(device.getMccmnc())) {
            final String[] mccmnc = device.getMccmnc().split("-");
            carrier = 0;
            if (mccmnc.length >= 2) {
                final String mcc = mccmnc[0];
                final String mnc = mccmnc[1];
                cellInfos.add(CellInfo.of(mcc, mnc));
                carrier = switch (mcc + mnc) {
                    case "46001", "46006" -> 1;
                    case "46000", "46002", "46007" -> 2;
                    case "46003", "46005", "46011" -> 3;
                    default -> 99;
                };
            }
        }

        final Integer type = device.getConnectiontype() == null
                ? DEFAULT_UNKNOWN_NETWORK_TYPE
                : device.getConnectiontype();

        return Network.builder()
                .type(type)
                .cellInfo(cellInfos)
                .carrier(carrier)
                .build();
    }
}
