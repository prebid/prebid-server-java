package org.prebid.server.hooks.modules.rule.engine.core.request.result.functions.filter;

import com.iab.openrtb.request.Imp;
import lombok.Value;

import java.util.Set;

@Value(staticConstructor = "of")
public class FilterBiddersResult {

    Imp imp;

    Set<String> bidders;
}
