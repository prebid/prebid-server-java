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

    Integer text = 1;
    Integer bigPicture = 2;
    Integer bigPicture2 = 3;
    Integer gif = 4;
    Integer videoText = 6;
    Integer smallPicture = 7;
    Integer threeSmallPicturesText = 8;
    Integer video = 9;
    Integer iconText = 10;
    Integer videoWithPicturesText = 11;

    Integer appPromotion = 3;


    Integer banner = 8;
    Integer xNative = 3;
    Integer roll = 60;
    Integer interstitial = 12;
    Integer rewarded = 7;
    Integer splash = 1;
    Integer magazinelock = 2;
    Integer audio = 17;
}
