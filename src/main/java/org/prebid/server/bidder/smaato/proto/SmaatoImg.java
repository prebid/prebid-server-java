package org.prebid.server.bidder.smaato.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class SmaatoImg {

    String url;

    Integer w;

    Integer h;

    String ctaurl;
}
