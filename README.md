# ICON Test Suite

## Quick Start

Prepare an ICON node (either using a `goloop/gochain-icon` docker image or running it as a standalone server) that accepts requests from the test framework.

If you want to use the `goloop/gochain-icon` docker image, please refer to [gochain-local](https://github.com/icon-project/gochain-local) project.
```bach
$ ./run_gochain.sh start
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
