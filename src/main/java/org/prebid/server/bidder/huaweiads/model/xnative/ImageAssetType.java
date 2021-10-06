package org.prebid.server.bidder.huaweiads.model.xnative;

public enum ImageAssetType {

    // Icon; Icon image; Optional. Max height: at least 50; aspect ratio: 1:1
    IMAGE_ASSET_TYPE_ICON(1),

    // Logo; Logo image for the brand/app. Deprecated in version 1.2 - use type 1 Icon.
    IMAGE_ASSET_TYPE_LOGO(2),

    // Main; Large image preview for the ad. At least one of 2 size variants required:
    //   Small Variant:
    //     max height: at least 200
    //     max width: at least 200, 267, or 382
    //     aspect ratio: 1:1, 4:3, or 1.91:1
    //   Large Variant:
    //     max height: at least 627
    //     max width: at least 627, 836, or 1198
    //     aspect ratio: 1:1, 4:3, or 1.91:1
    IMAGE_ASSET_TYPE_MAIN(3);

    // 500+ XXX; Reserved for Exchange specific usage numbered above 500. No recommendations

    private final Integer value;

    ImageAssetType(int value) {
        this.value = value;
    }

    public Integer getValue() {
        return this.value;
    }

}
