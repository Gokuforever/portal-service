# Project Dependencies

## Overview

This document lists all dependencies used in the portal-service multi-module project.

## Dependency Management

All dependency versions are centrally managed in the parent POM (`pom.xml`) under `<dependencyManagement>`.

---

## Common Libraries Module (common-libs)

### Spring Framework
| Dependency | Purpose | Scope |
|------------|---------|-------|
| spring-boot-starter | Core Spring Boot functionality | compile |
| spring-boot-starter-web | Web application support, REST APIs | compile |
| spring-boot-starter-data-mongodb | MongoDB integration | compile |
| spring-boot-starter-security | Security framework | compile |
| spring-boot-starter-aop | Aspect-Oriented Programming | compile |
| spring-boot-starter-test | Testing framework | test |

### Security & Authentication
| Dependency | Version | Purpose |
|------------|---------|---------|
| jjwt-api | 0.11.5 | JWT API |
| jjwt-impl | 0.11.5 | JWT implementation |
| jjwt-jackson | 0.11.5 | JWT JSON processing |

### Utilities
| Dependency | Version | Purpose |
|------------|---------|---------|
| lombok | 1.18.30 | Reduce boilerplate code |
| guava | 32.1.3-jre | Google core libraries |
| commons-lang3 | (Spring managed) | Apache Commons utilities |
| jetbrains-annotations | 24.0.1 | JetBrains annotations (@NotNull, etc.) |

### JSON & Data Processing
| Dependency | Version | Purpose |
|------------|---------|---------|
| jackson-databind | (Spring managed) | JSON serialization/deserialization |
| gson | 2.10.1 | Google JSON library |

### PDF Generation
| Dependency | Version | Purpose |
|------------|---------|---------|
| openpdf | 1.3.30 | PDF generation (iText fork) |

**Note:** OpenPDF uses the `com.lowagie.text` package (compatible with older iText code).

### HTTP & Networking
| Dependency | Version | Purpose |
|------------|---------|---------|
| httpclient5 | (Spring managed) | Apache HTTP client |

### Logging
| Dependency | Version | Purpose |
|------------|---------|---------|
| log4j-api | 2.20.0 | Logging API |

---

## Portal Service Module (portal-service)

### Internal Dependencies
| Dependency | Purpose |
|------------|---------|
| common-libs | Shared library module (internal) |

### Spring Framework
| Dependency | Purpose | Scope |
|------------|---------|-------|
| spring-boot-starter-web-services | SOAP web services support | compile |
| spring-boot-starter-aop | AOP support | compile |
| spring-boot-devtools | Development tools | runtime |
| spring-boot-starter-test | Testing framework | test |

### Payment Gateways
| Dependency | Version | Purpose |
|------------|---------|---------|
| pg-sdk-java (PhonePe) | 2.1.0 | PhonePe payment integration |

### Utilities
| Dependency | Version | Purpose |
|------------|---------|---------|
| lombok | 1.18.30 | Reduce boilerplate code |
| guava | 32.1.3-jre | Google core libraries |
| log4j-api | 2.20.0 | Logging API |

---

## Version Properties (Parent POM)

```xml
<properties>
    <java.version>17</java.version>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    
    <!-- Dependency Versions -->
    <lombok.version>1.18.30</lombok.version>
    <guava.version>32.1.3-jre</guava.version>
    <phonepe.version>2.1.0</phonepe.version>
    <log4j.version>2.20.0</log4j.version>
    <jjwt.version>0.11.5</jjwt.version>
    <jetbrains-annotations.version>24.0.1</jetbrains-annotations.version>
    <openpdf.version>1.3.30</openpdf.version>
    <gson.version>2.10.1</gson.version>
</properties>
```

---

## Dependency Tree

```
portal-parent
│
├── common-libs
│   ├── Spring Boot Starter
│   ├── Spring Boot Starter Web
│   ├── Spring Data MongoDB
│   ├── Spring Security
│   ├── Spring AOP
│   ├── JWT (api, impl, jackson)
│   ├── Lombok
│   ├── Guava
│   ├── JetBrains Annotations
│   ├── Jackson Databind
│   ├── Gson
│   ├── OpenPDF
│   ├── Apache Commons Lang3
│   ├── Apache HttpClient5
│   ├── Log4j API
│   └── Spring Boot Test (test)
│
└── portal-service
    ├── common-libs (internal)
    ├── Spring Boot Web Services
    ├── Spring Boot AOP
    ├── Spring Boot DevTools (runtime)
    ├── PhonePe SDK
    ├── Lombok
    ├── Guava
    ├── Log4j API
    └── Spring Boot Test (test)
```

---

## Repository Configuration

### PhonePe SDK Repository
```xml
<repository>
    <id>io.cloudrepo</id>
    <name>PhonePe JAVA SDK</name>
    <url>https://phonepe.mycloudrepo.io/public/repositories/phonepe-pg-sdk-java</url>
</repository>
```

---

## Key Dependencies by Use Case

### MongoDB Operations
- `spring-boot-starter-data-mongodb`
- `jackson-databind` (for document mapping)

### REST API Development
- `spring-boot-starter-web`
- `jackson-databind` (JSON serialization)
- `gson` (alternative JSON processing)

### Security & Authentication
- `spring-boot-starter-security`
- `jjwt-api`, `jjwt-impl`, `jjwt-jackson` (JWT tokens)

### PDF Generation
- `openpdf` (invoice generation, reports)

### Payment Processing
- `pg-sdk-java` (PhonePe integration)

### Utilities
- `lombok` (reduce boilerplate)
- `guava` (collections, caching, utilities)
- `commons-lang3` (string utils, etc.)
- `jetbrains-annotations` (code annotations)

### HTTP Communication
- `httpclient5` (external API calls)

### Logging
- `log4j-api` (application logging)

### Testing
- `spring-boot-starter-test` (JUnit, Mockito, etc.)

---

## Transitive Dependencies

The following are automatically included via Spring Boot:

- **Spring Core**: spring-core, spring-context, spring-beans
- **Spring Web**: spring-web, spring-webmvc
- **Spring Data**: spring-data-commons, spring-data-mongodb
- **Jackson**: jackson-core, jackson-annotations, jackson-databind
- **Tomcat**: tomcat-embed-core (embedded server)
- **Logging**: logback-classic, slf4j-api
- **Validation**: hibernate-validator
- **MongoDB Driver**: mongodb-driver-sync

---

## Adding New Dependencies

### To common-libs:

1. Add version property to parent POM if needed:
```xml
<properties>
    <new-lib.version>1.0.0</new-lib.version>
</properties>
```

2. Add to parent POM's dependencyManagement:
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>new-lib</artifactId>
            <version>${new-lib.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

3. Add to common-libs/pom.xml (without version):
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>new-lib</artifactId>
</dependency>
```

### To portal-service:

Follow the same process, but add to `portal-service/pom.xml` instead.

---

## Dependency Analysis Commands

```bash
# View dependency tree
mvn dependency:tree

# View dependency tree for specific module
mvn dependency:tree -pl common-libs

# Analyze dependencies
mvn dependency:analyze

# Check for updates
mvn versions:display-dependency-updates

# Resolve dependencies
mvn dependency:resolve
```

---

## Excluded Dependencies

The following dependencies are explicitly excluded:

### Lombok (from Spring Boot plugin)
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <excludes>
            <exclude>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
            </exclude>
        </excludes>
    </configuration>
</plugin>
```

---

## License Information

| Dependency | License |
|------------|---------|
| Spring Boot | Apache 2.0 |
| Lombok | MIT |
| Guava | Apache 2.0 |
| Jackson | Apache 2.0 |
| Gson | Apache 2.0 |
| OpenPDF | LGPL/MPL |
| JWT | Apache 2.0 |
| Apache Commons | Apache 2.0 |
| JetBrains Annotations | Apache 2.0 |
| PhonePe SDK | Proprietary |

---

## Security Considerations

### Regular Updates
- Monitor for security vulnerabilities
- Update dependencies regularly
- Use `mvn versions:display-dependency-updates`

### Known Vulnerabilities
Check dependencies for known vulnerabilities:
```bash
mvn org.owasp:dependency-check-maven:check
```

---

## Troubleshooting

For dependency-related issues, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

Common issues:
- Missing package errors → Check if dependency is added
- Version conflicts → Check dependency tree
- Build failures → Clean and rebuild

---

Last Updated: 2026-02-28
