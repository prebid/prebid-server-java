package org.prebid.server.bidder.huaweiads.model.request;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ExtraInfo {

    List<PkgNameConvert> pkgNameConvert;

    String closeSiteSelectionByCountry;
}
