# Portal Service - Multi-Module Project

This is a multi-module Maven project for the Sorted Portal Service.

## Project Structure

```
portal-service/                          (Root/Parent Project)
│
├── pom.xml                              (Parent POM - manages all modules)
│
├── common-libs/                         (Shared Libraries Module)
│   ├── src/
│   │   ├── main/java/com/sorted/common/
│   │   │   ├── constants/              # Application constants
│   │   │   ├── exceptions/             # Custom exceptions
│   │   │   └── utils/                  # Utility classes
│   │   └── test/
│   ├── pom.xml
│   └── README.md
│
└── portal-service/                      (Main Service Module)
    ├── src/
    │   ├── main/
    │   │   ├── java/com/sorted/portal/
    │   │   └── resources/
    │   └── test/
    ├── pom.xml
    ├── Dockerfile
    └── docker-compose.yml
```

## Modules

### 1. Parent Module (portal-parent)
- **Artifact ID**: `portal-parent`
- **Packaging**: `pom`
- **Purpose**: Manages common dependencies, plugins, and module coordination
- **Key Features**:
  - Centralized dependency version management
  - Common build configuration
  - Repository definitions

### 2. Common Libraries (common-libs)
- **Artifact ID**: `common-libs`
- **Packaging**: `jar`
- **Purpose**: Shared utilities and common code
- **Contains**:
  - String utilities
  - Common constants
  - Custom exceptions
  - Reusable components

### 3. Portal Service (portal-service)
- **Artifact ID**: `portal-service`
- **Packaging**: `jar`
- **Purpose**: Main application service
- **Dependencies**: Uses `common-libs` module

## Building the Project

### Build All Modules
```powershell
# From root directory
mvn clean install
```

### Build Specific Module
```powershell
# Build only common-libs
cd common-libs
mvn clean install

# Build only portal-service
cd portal-service
mvn clean package
```

### Skip Tests
```powershell
mvn clean install -DskipTests
```

## Running the Application

### Using Maven
```powershell
cd portal-service
mvn spring-boot:run
```

### Using JAR
```powershell
cd portal-service/target
java -jar portal-service-0.0.1-SNAPSHOT.jar
```

### Using Docker
```powershell
cd portal-service
docker-compose up
```

## Development Workflow

### Adding a New Utility to common-libs

1. Create your utility class:
```java
// common-libs/src/main/java/com/sorted/common/utils/MyUtil.java
package com.sorted.common.utils;

@UtilityClass
public class MyUtil {
    public static String doSomething(String input) {
        // implementation
    }
}
```

2. Build common-libs:
```powershell
cd common-libs
mvn clean install
```

3. Use in portal-service:
```java
import com.sorted.common.utils.MyUtil;

String result = MyUtil.doSomething("test");
```

### Adding a New Module

1. Create module directory and structure
2. Create module's `pom.xml` with parent reference
3. Add module to parent POM's `<modules>` section
4. Run `mvn clean install` from root

## IDE Setup

### IntelliJ IDEA
1. Open the root `pom.xml` as a project
2. IntelliJ will automatically detect all modules
3. Wait for Maven sync to complete

### Eclipse
1. File → Import → Existing Maven Projects
2. Select root directory
3. Eclipse will detect all modules automatically

### VS Code
1. Open root directory
2. Install "Java Extension Pack"
3. Maven will be detected automatically

## Dependency Management

### Adding a Dependency

#### To All Modules (via Parent)
Add to parent POM's `<dependencyManagement>`:
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>example-lib</artifactId>
            <version>1.0.0</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

#### To Specific Module
Add to module's POM (version inherited from parent):
```xml
<dependencies>
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>example-lib</artifactId>
    </dependency>
</dependencies>
```

## Testing

### Run All Tests
```powershell
mvn test
```

### Run Tests for Specific Module
```powershell
cd portal-service
mvn test
```

## Continuous Integration

### Build Order
Maven automatically determines the correct build order based on dependencies:
1. `common-libs` (no dependencies on other modules)
2. `portal-service` (depends on common-libs)

### CI/CD Pipeline Example
```yaml
# Example GitHub Actions workflow
- name: Build with Maven
  run: mvn clean install -B
  
- name: Run Tests
  run: mvn test -B
  
- name: Build Docker Image
  run: |
    cd portal-service
    docker build -t portal-service:latest .
```

## Best Practices

1. **Keep common-libs lightweight**: Only include truly shared code
2. **Version management**: Define versions in parent POM
3. **Module independence**: Avoid circular dependencies
4. **Documentation**: Update module READMEs when adding features
5. **Testing**: Write tests for utilities in common-libs
6. **Build from root**: Always build from root to ensure consistency

## Troubleshooting

### Module Not Found
```powershell
# Reinstall all modules
mvn clean install -U
```

### Dependency Resolution Issues
```powershell
# Force update dependencies
mvn clean install -U

# Clear local repository cache
rm -rf ~/.m2/repository/com/sorted
mvn clean install
```

### IDE Not Recognizing Modules
- IntelliJ: File → Invalidate Caches → Restart
- Eclipse: Right-click project → Maven → Update Project
- VS Code: Reload window (Ctrl+Shift+P → Reload Window)

## Future Enhancements

Potential modules to add:
- `api-gateway`: API gateway service
- `admin-service`: Admin panel backend
- `notification-service`: Notification handling
- `common-models`: Shared data models
- `common-security`: Security utilities

## Support

For issues or questions:
1. Check MIGRATION_GUIDE.md
2. Review module-specific README files
3. Contact the development team

## Version History

- **0.0.1-SNAPSHOT**: Initial multi-module setup
  - Created parent POM structure
  - Added common-libs module
  - Restructured portal-service as module
