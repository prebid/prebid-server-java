package org.prebid.server.deals.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.deals.lineitem.LineItem;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class MatchLineItemsResult {

    List<LineItem> lineItems;
}
