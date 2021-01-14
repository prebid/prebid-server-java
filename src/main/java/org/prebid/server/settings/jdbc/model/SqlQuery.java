package org.prebid.server.settings.jdbc.model;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class SqlQuery {

    String query;

    List<Object> parameters;
}
