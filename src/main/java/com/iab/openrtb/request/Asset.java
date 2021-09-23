package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;

@Builder(toBuilder = true)
@Value
@Getter
@Setter
public class Asset {

    public static final Asset EMPTY = com.iab.openrtb.request.Asset.builder().build();

    Integer id;

    Integer required;

    TitleObject title;

    ImageObject img;

    VideoObject video;

    DataObject data;

    ObjectNode ext;
}
