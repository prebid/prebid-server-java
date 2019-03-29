package org.prebid.server.it;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.VertxTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.config.additional-location=sample-prebid-config.yaml",
        "http.port=8081",
        "admin.port=8061"
})
@RunWith(SpringRunner.class)
public class SanityTest extends VertxTest {

    @Test
    public void run() {
        // this test is just to up and run application with minimal config
    }
}
