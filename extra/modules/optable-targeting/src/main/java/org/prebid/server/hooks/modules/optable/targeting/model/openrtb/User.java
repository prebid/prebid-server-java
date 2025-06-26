package org.prebid.server.hooks.modules.optable.targeting.model.openrtb;

import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Eid;
import lombok.Value;

import java.util.List;

@Value
public class User {

    List<Eid> eids;

    List<Data> data;
}
