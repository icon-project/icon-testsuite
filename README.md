# ICON Test Suite

## Quick Start

Prepare a ICON node (either using a T-Bears docker image or running it as a standalone server) that accepts requests from the test framework.

If you want to use the T-Bears docker image, start the container before running testing as follows.
```bach
$ docker run -it -p 9000:9000 iconloop/tbears:mainnet
```

Run the all test cases.
```bach
$ ./gradlew test
```

You can enable filtering via the `--tests` command-line option.
For example, the following command line runs only the `CrowdsaleTest` case.
```bach
$ ./gradlew test --tests CrowdsaleTest
```
For more information, refer to [Testing in Java & JVM projects](https://docs.gradle.org/current/userguide/java_testing.html).
