# Common Libraries Module

This module contains shared utilities, constants, and common functionality used across the portal services.

## Structure

```
common-libs/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/sorted/common/
│   │   │       ├── constants/      # Application-wide constants
│   │   │       ├── exceptions/     # Custom exception classes
│   │   │       └── utils/          # Utility classes
│   │   └── resources/
│   └── test/
│       └── java/
└── pom.xml
```

## Components

### Constants
- **CommonConstants**: Application-wide constants for dates, HTTP headers, content types, etc.

### Exceptions
- **BusinessException**: Custom exception for business logic errors

### Utils
- **StringUtils**: String manipulation and validation utilities

## Usage

Add this module as a dependency in your service's `pom.xml`:

```xml
<dependency>
    <groupId>com.sorted</groupId>
    <artifactId>common-libs</artifactId>
</dependency>
```

## Adding New Utilities

1. Create your utility class in the appropriate package
2. Use `@UtilityClass` annotation from Lombok for utility classes
3. Add unit tests in the corresponding test package
4. Update this README with the new component

## Best Practices

- Keep utilities stateless and thread-safe
- Use meaningful names and add JavaDoc comments
- Write unit tests for all utility methods
- Avoid dependencies on service-specific code
