package org.prebid.server.bidder.huaweiads.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@Builder(toBuilder = true)
@AllArgsConstructor
public class PkgNameConvert {

    String convertedPkgName;

    List<String> unconvertedPkgNames;

    List<String> unconvertedPkgNameKeyWords;

    List<String> unconvertedPkgNamePrefixs;

    List<String> exceptionPkgNames;
}
