package com.iab.openrtb.request.video;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class PodError {

    Integer podId;

    Integer podIndex;

    List<String> podErrors;
}
