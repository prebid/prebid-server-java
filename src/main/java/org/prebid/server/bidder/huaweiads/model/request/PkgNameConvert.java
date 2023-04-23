package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public class PkgNameConvert {

    String convertedPkgName;

    List<String> unconvertedPkgNames;

    List<String> unconvertedPkgNameKeyWords;

    List<String> unconvertedPkgNamePrefixs;

    List<String> exceptionPkgNames;

}
