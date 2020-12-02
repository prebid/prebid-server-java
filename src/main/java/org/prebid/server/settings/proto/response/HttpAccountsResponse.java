package org.prebid.server.settings.proto.response;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.settings.model.Account;

import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class HttpAccountsResponse {

    Map<String, Account> accounts;
}
