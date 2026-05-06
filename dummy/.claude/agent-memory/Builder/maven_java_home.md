---
name: Maven JAVA_HOME quirk on this machine
description: System mvn defaults to an older JDK; must export JAVA_HOME=$(/usr/libexec/java_home -v 21) before any mvn command
type: reference
---

The host has openjdk@11, @17, and @21 installed via Homebrew. `java -version` reports 21, but `mvn` picks an older one by default and fails with `release version 21 not supported`.

**How to apply:** Always prefix mvn commands with `export JAVA_HOME=$(/usr/libexec/java_home -v 21);` for this project (Java 21, Spring Boot 3.4.1).

The corporate Artifactory mirror at `artifactorybase.service.csnzoo.com` is also unreachable from this machine, so `mvn package` fails for the maven-jar-plugin download — `mvn test` and `mvn compile` work fine because the test/compile plugins are already cached. Use offline (`-o`) when possible.
