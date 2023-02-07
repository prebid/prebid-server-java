package org.prebid.server.bidder.rubicon.proto.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Builder(toBuilder = true)
@Value
public class RubiconBid {

    String id;

    String impid;

    BigDecimal price;

    String nurl;

    String burl;

    String lurl;

    String adm;

    String adid;

    List<String> adomain;

    String bundle;

    String iurl;

    String cid;

    String crid;

    List<String> cat;

    List<Integer> attr;

    Integer api;

    Integer protocol;

    Integer qagmediarating;

    String language;

    String dealid;

    Integer w;

    Integer h;

    Integer wratio;

    Integer hratio;

    Integer exp;

    ObjectNode ext;

    ObjectNode admNative;
}
