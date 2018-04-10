package org.prebid.server.metric.dropwizard;

import com.codahale.metrics.Counter;

class ResettingCounter extends Counter {

    @Override
    public long getCount() {
        final long count = super.getCount();
        dec(count);
        return count;
    }
}
