package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class Network {

    Integer type;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Integer carrier;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<CellInfo> cellInfo;
}
