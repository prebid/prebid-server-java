package org.prebid.server.bidder.smaato.proto;

import lombok.Value;

@Value(staticConstructor = "of")
public class SmaatoUserExtData {

    String keywords;

    String gender;

    Integer yob;
}
