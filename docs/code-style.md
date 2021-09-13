# Code Style
The intent here is to maintain a common style across the project
and rely on the process to enforce it instead of individuals.

## Automated code style check 
The [pom.xml](../pom.xml) is configured to enforce a coding style 
defined in [checkstyle.xml](../checkstyle.xml) when maven `validate` phase executed.

## Formatting
The project uses formatting rules described in `.editorconfig` file. Most of the popular IDEs have support of it.
For example, in `IntelliJ IDEA` hit `Code -> Reformat Code` to organize your code.

## Code conventions

### Max line length
Line length is limited to 120 columns for `*.java` files. Other file types are unrestricted.

### Transitive dependencies
Don't use transitive dependencies in project code.
If it needed, recommended adding a library as a dependency of Maven in `pom.xml` directly.

### Add library as maven dependency
It is recommended to define version of library to separate property in `pom.xml`:
```
<project>
    <properties>
        <caffeine.version>2.6.2</caffeine.version>
    </properties>
    
    <dependencies>
        <dependency>
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>caffeine</artifactId>
            <version>${caffeine.version}</version>
        </dependency>
    </dependencies>
</project>
```

### Avoid wildcards in imports
Do not use wildcard in imports because they hide what exactly is required by the class.
```
// bad
import java.util.*;


// good
import java.util.HashMap;
import java.util.Map;
```

### Variable and method naming
Preferred to use `camelCase` naming convention for variables and methods.

Name of variable should be self-explanatory:
```
// bad
String s = resolveParamA();

// good
String resolvedParamA = resolveParamA();
```
This helps to other developers flesh your code out better without additional questions.

For `Map`s it is recommended to use `To` between key and value designation:
```
// bad
Map<Imp, ExtImp> map = getData();

// good
Map<Imp, ExtImp> impToExt = getData();
```

### Make variables final
It is recommended to declare variable as `final`- not strict but rather project convention to keep the code safe.
```
// bad
String value = "value";

// good
final String value = "value";
```

### Ternary expressions
Results of long ternary operators should be on separate lines:
```
// bad
boolean result = someVeryVeryLongConditionThatForcesLineWrap ? firstResult
    : secondResult;

// good
boolean result = someVeryVeryLongConditionThatForcesLineWrap
    ? firstResult
    : secondResult;
```

No so strict, but short ternary operations should be in one line:
```
// bad
boolean result = someShortCondition
    ? firstResult
    : secondResult;

// good
boolean result = someShortCondition ? firstResult : secondResult;
```

### Nested method calls
Try to avoid hard-readable multiple nested method calls:
```
// bad
int resolvedValue = resolveValue(fetchExternalJson(url, httpClient), populateAdditionalKeys(mainKeys, keyResolver));

// good
String externalJson = fetchExternalJson(url, httpClient);
List<Key> additionalKeys = fetchAdditionalKeys(mainKeys, keyResolver);
int resolvedValue = resolveValue(externalJson, additionalKeys);
```

### Check for NULL
If you're dealing with incoming data, please ensure to check if nested object is not null before chaining.
```
// bad
final ExtRequestTargeting targeting = bidRequest.getExt().getPrebid().getTargeting();

// good
final ExtRequest requestExt = bidRequest.getExt();
final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
final ExtRequestTargeting targeting = prebid != null ? prebid.getTargeting() : null;
```
For convenience, the `org.prebid.server.util.ObjectUtils` helper can be used for such kind of operations.

### Garbage code
Don't leave commented code (don't think about the future). You can always add it later when it will be really desired. 
```
// bad
// String iWillUseThisLater = "never";
```

### Privacy
It is strictly prohibited to log any of private data about publisher, exchanges or similar sensitive information.
The idea is to keep this open-source project safe as far as possible.

### Tests
The code should be covered over 90%.

The common way for writing tests is to comply with `given-when-then` style.
```
// given
final BidRequest bidRequest = BidRequest.builder().id("").build();

// when
final ValidationResult result = requestValidator.validate(bidRequest);

// then
assertThat(result.getErrors()).containsOnly("request missing required field: \"id\"");
```

Don't use real data in tests, like endpoint URLs, account IDs, etc.
```
// bad
String ENDPOINT_URL = "https://prebid.org";

// good
String ENDPOINT_URL = "https://test-endpoint.url";
```
