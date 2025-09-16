package org.prebid.server.settings.proto.response;

import lombok.Value;
import org.prebid.server.settings.model.Account;

import java.util.Map;

@Value(staticConstructor = "of")
public class HttpAccountsResponse {

    Map<String, Account> accounts;
}
