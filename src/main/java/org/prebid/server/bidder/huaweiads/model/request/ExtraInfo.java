package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ExtraInfo {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<PkgNameConvert> pkgNameConvert;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String closeSiteSelectionByCountry;

    public static class PkgNameConvert {

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        String convertedPkgName;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<String> unconvertedPkgNames;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<String> unconvertedPkgNameKeyWords;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<String> unconvertedPkgNamePrefixs;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<String> exceptionPkgNames;
    }
}
