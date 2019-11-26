package com.iab.openrtb.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class Asset {

    Integer id;

    Integer required;

    TitleObject title;

    ImageObject img;

    VideoObject video;

    DataObject data;

    Link link;

    ObjectNode ext;
}
