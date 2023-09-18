package org.prebid.server;

import lombok.SneakyThrows;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SuppressWarnings("checkstyle:hideutilityclassconstructor")
@SpringBootApplication
public class Application {

    static volatile int test = 0;

    @SneakyThrows
    public static void main(String[] args) {
        for (int i = 0; i < 60; i++) {
            new Thread(() -> {
                while (true) {
                    test++;
                }
            }).start();
        }
        SpringApplication.run(Application.class, args);
    }
}
