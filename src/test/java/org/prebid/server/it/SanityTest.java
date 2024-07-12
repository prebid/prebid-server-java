package org.prebid.server.it;

import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
// This will create new application context, so used ports must be changed (0 means random available local port).
@TestPropertySource(properties = {
        "spring.config.additional-location=sample/configs/prebid-config.yaml", "server.http.port=0", "admin.port=0"})
public class SanityTest extends VertxTest {

    @Test
    public void run() {
        // this test is just to up and run application with minimal config
    }
}
