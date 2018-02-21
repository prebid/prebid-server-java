package org.rtb.vexing.model.response;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public final class CookieSyncResponse {

    String uuid;

    String status;

    List<BidderStatus> bidderStatus;
}
