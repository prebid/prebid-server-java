package org.prebid.server.bidder.huaweiads.model.util;

public interface HuaweiAdsConstants {

    String API_VERSION = "3.4";
    String DEFAULT_COUNTRY_NAME = "ZA";
    String DEFAULT_CURRENCY = "CNY";
    Integer DEFAULT_UNKNOWN_NETWORK_TYPE = 0;
    Integer IMAGE_ASSET_TYPE_MAIN = 3;
    Integer IMAGE_ASSET_TYPE_ICON = 1;
    Integer DATA_ASSET_TYPE_DESC = 2;
    Integer DATA_ASSET_TYPE_DESC2 = 10;
    String DEFAULT_TIME_ZONE = "+0200";
    String DEFAULT_MODEL_NAME = "HUAWEI";
    String CHINESE_SITE_ENDPOINT = "https://acd.op.hicloud.com/ppsadx/getResult";
    String EUROPEAN_SITE_ENDPOINT = "https://adx-dre.op.hicloud.com/ppsadx/getResult";
    String ASIAN_SITE_ENDPOINT = "https://adx-dra.op.hicloud.com/ppsadx/getResult";
    String RUSSIAN_SITE_ENDPOINT = "https://adx-drru.op.hicloud.com/ppsadx/getResult";
}
