package org.prebid.server.bidder.huaweiads;

import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.huaweiads.model.request.App;
import org.prebid.server.bidder.huaweiads.model.request.PkgNameConvert;
import org.prebid.server.exception.PreBidException;

import java.util.List;
import java.util.Optional;

public class HuaweiAppBuilder {

    private static final String DEFAULT_LANGUAGE = "en";

    private final List<PkgNameConvert> packageNameConverter;

    public HuaweiAppBuilder(List<PkgNameConvert> packageNameConverter) {
        this.packageNameConverter = packageNameConverter;
    }

    public App build(com.iab.openrtb.request.App app, String countryCode) throws PreBidException {
        if (app == null) {
            return null;
        }

        return App.builder()
                .country(countryCode)
                .version(HuaweiUtils.getIfNotBlank(app.getVer()).orElse(null))
                .name(HuaweiUtils.getIfNotBlank(app.getName()).orElse(null))
                .pkgName(HuaweiUtils.getIfNotBlank(app.getBundle())
                        .map(this::getPackageName)
                        .orElseThrow(() -> new PreBidException("generate HuaweiAds AppInfo failed: "
                                + "openrtb BidRequest.App.Bundle is empty.")))
                .lang(Optional.ofNullable(app.getContent())
                        .flatMap(content -> HuaweiUtils.getIfNotBlank(content.getLanguage()))
                        .orElse(DEFAULT_LANGUAGE))
                .build();
    }

    private String getPackageName(String bundleName) {
        for (PkgNameConvert convert : packageNameConverter) {
            final String convertedPkgName = convert.getConvertedPkgName();
            if (StringUtils.isBlank(convertedPkgName)) {
                continue;
            }

            for (String name : convert.getExceptionPkgNames()) {
                if (bundleName.equals(name)) {
                    return bundleName;
                }
            }

            for (String name : convert.getUnconvertedPkgNames()) {
                if (bundleName.equals(name) || "*".equals(name)) {
                    return convertedPkgName;
                }
            }

            for (String keyword : convert.getUnconvertedPkgNameKeyWords()) {
                //todo: why is 0 ignored?
                if (bundleName.indexOf(keyword) > 0) {
                    return convertedPkgName;
                }
            }

            for (String prefix : convert.getUnconvertedPkgNamePrefixs()) {
                if (bundleName.startsWith(prefix)) {
                    return convertedPkgName;
                }
            }
        }

        return bundleName;
    }

}
