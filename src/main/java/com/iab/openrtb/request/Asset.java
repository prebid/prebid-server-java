package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Builder(toBuilder = true)
@Value
@NonFinal
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Asset {

    public static final Asset EMPTY = Asset.builder().build();

    Integer id;

    Integer required;

    TitleObject title;

    ImageObject img;

    VideoObject video;

    DataObject data;

    ObjectNode ext;
}
