package org.prebid.server.bidder.huaweiads.model.request;

public enum CreativeType {

    TEXT(1), BIG_PICTURE(2), BIG_PICTURE_2(3), GIF(4), VIDEO_TEXT(6), SMALL_PICTURE(7),
    THREE_SMALL_PICTURES_TEXT(8), VIDEO(9), ICON_TEXT(10), VIDEO_WITH_PICTURES_TEXT(11);

    private final Integer code;

    CreativeType(Integer code) {
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

    public static CreativeType fromCreativeTypeCode(int creativeTypeCode) {
        return switch (creativeTypeCode) {
            case 2 -> CreativeType.BIG_PICTURE;
            case 3 -> CreativeType.BIG_PICTURE_2;
            case 4 -> CreativeType.GIF;
            case 6 -> CreativeType.VIDEO_TEXT;
            case 7 -> CreativeType.SMALL_PICTURE;
            case 8 -> CreativeType.THREE_SMALL_PICTURES_TEXT;
            case 9 -> CreativeType.VIDEO;
            case 10 -> CreativeType.ICON_TEXT;
            case 11 -> CreativeType.VIDEO_WITH_PICTURES_TEXT;
            default -> CreativeType.TEXT;
        };
    }
}
