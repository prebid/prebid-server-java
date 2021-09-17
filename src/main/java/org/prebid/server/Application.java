package org.prebid.server;

import com.iab.openrtb.request.BidRequest;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SuppressWarnings("checkstyle:hideutilityclassconstructor")
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
