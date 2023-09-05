package org.prebid.server.bidder.huaweiads.model.request;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class PkgNameConvert {

    String convertedPkgName;

    List<String> unconvertedPkgNames;

    List<String> unconvertedPkgNameKeyWords;

    List<String> unconvertedPkgNamePrefixs;

    List<String> exceptionPkgNames;
}
