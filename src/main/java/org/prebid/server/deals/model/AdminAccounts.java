package org.prebid.server.deals.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@Value
@AllArgsConstructor(staticName = "of")
public class AdminAccounts {

    List<String> accounts;
}
