## Unit tests

Project unit tests use [JUnit](https://junit.org) as a main testing framework. 
Also used [AssertJ](http://joel-costigliola.github.io/assertj) for checking assertions 
and [Mockito](http://mockito.org) for mocking objects.

In general, for testing the behavior of some class use the following rules:
- check instance creation of tested object (if non-static)
- check all negative scenarios with possible exceptions
- check all positive scenarios

Writing the unit tests considers [Given-When-Then](https://martinfowler.com/bliki/GivenWhenThen.html) style.
For example:

```
@Test
public void someTest() {
    // given
    int a = 2;
    int b = 2;
    
    // when
    int c = a + b;
    
    // then
    assertThat(c).isEqualTo(4);
}
```

It is preferable to use fluent assertions:
```
assertThat(result)
    .isNotNull()
    .extracting(r -> r.errors).isEmpty();
```

Unit tests can use static method imports, like:
```
import static java.util.Collections.emptyList;

//...

someService.perform(emptyList());
```

Unit tests can use resources from "src/test/resources" folder.

In case your business logic uses JSON manipulation, unit tests must be inherited from `org.prebid.server.VertxTest` class.
Thus, application preconfigured JSON mapper will be used. 

To run the project unit tests execute:
```
mvn clean test
```
