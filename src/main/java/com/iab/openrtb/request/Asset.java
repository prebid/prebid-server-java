package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class Asset {
    Integer id;

    Integer required;

    TitleObject title;

    ImageObject img;

    VideoObject video;

    DataObject data;

    ObjectNode ext;
}
