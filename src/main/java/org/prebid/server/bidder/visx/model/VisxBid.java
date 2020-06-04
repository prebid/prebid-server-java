package org.prebid.server.bidder.visx.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Builder(toBuilder = true)
@Data
public class VisxBid {

    String impid;

    BigDecimal price;

    Integer uid;

    String crid;

    String adm;

    List<String> adomain;

    String dealid;

    Integer w;

    Integer h;
}
