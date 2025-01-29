package org.prebid.server;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.execution.ruleengine.Mutation;
import org.prebid.server.execution.ruleengine.MutationFactory;
import org.prebid.server.execution.ruleengine.extractors.BooleanExtractor;
import org.prebid.server.execution.ruleengine.extractors.IntegerExtractor;
import org.prebid.server.execution.ruleengine.extractors.StringExtractor;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;
import java.util.Map;

@SuppressWarnings("checkstyle:hideutilityclassconstructor")
@SpringBootApplication
public class Application {

    @Value
    @Builder(toBuilder = true)
    private static class Test {

        String name;

        int version;

        boolean test;
    }

    public static void main(String[] args) {
        final BooleanExtractor<Test> isTestExtractor = BooleanExtractor.of(test -> test.test);
        final IntegerExtractor<Test> versionExtractor = IntegerExtractor.of(test -> test.version);
        final StringExtractor<Test> nameExtractor = StringExtractor.of(test -> test.name);

        final Mutation<Test> mutation = new MutationFactory<Test>().buildMutation(
                List.of(isTestExtractor, versionExtractor, nameExtractor),
                Map.of(
                        "false|12|hello", test -> test.toBuilder().name("1").build(),
                        "true|5|test", test -> test.toBuilder().name("2").build(),
                        "false|12|*", test -> test.toBuilder().name("3").build(),
                        "true|1|hello", test -> test.toBuilder().name("4").build(),
                        "true|-2|aloha", test -> test.toBuilder().name("5").build(),
                        "true|-2|hello", test -> test.toBuilder().name("6").build(),
                        "false|*|*", test -> test.toBuilder().name("7").build()));

        final Test mutated = mutation.mutate(new Test("bruh", 123, false));
        System.out.println(mutated);
    }
}
