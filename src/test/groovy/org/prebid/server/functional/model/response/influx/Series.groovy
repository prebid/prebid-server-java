package org.prebid.server.functional.model.response.influx

class Series {

    String name
    Tags tags
    List<String> columns
    List<List<String>> values
}
