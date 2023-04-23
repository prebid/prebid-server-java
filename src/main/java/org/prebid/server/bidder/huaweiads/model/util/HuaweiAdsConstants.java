package org.prebid.server.bidder.huaweiads.model.util;

public interface HuaweiAdsConstants {

    String API_VERSION = "3.4";
    String DEFAULT_COUNTRY_NAME = "ZA";
    Integer DEFAULT_UNKNOWN_NETWORK_TYPE = 0;
    String TIME_FORMAT = "2006-01-02 15:04:05.000";
    String DEFAULT_TIME_ZONE = "+0200";
    String DEFAULT_MODEL_NAME = "HUAWEI";
    String CHINESE_SITE_ENDPOINT = "https://acd.op.hicloud.com/ppsadx/getResult";
    String EUROPEAN_SITE_ENDPOINT = "https://adx-dre.op.hicloud.com/ppsadx/getResult";
    String ASIAN_SITE_ENDPOINT = "https://adx-dra.op.hicloud.com/ppsadx/getResult";
    String RUSSIAN_SITE_ENDPOINT = "https://adx-drru.op.hicloud.com/ppsadx/getResult";
}
