package com.iab.openrtb.request.video;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class PodError {

    Integer podId;

    Integer podIndex;

    List<String> podErrors;
}

