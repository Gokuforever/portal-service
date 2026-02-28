# Troubleshooting Guide

## Common Build Issues and Solutions

### 1. Package org.jetbrains.annotations does not exist

**Error:**
```
package org.jetbrains.annotations does not exist
```

**Solution:**
Added JetBrains annotations dependency to `common-libs/pom.xml`:
```xml
<dependency>
    <groupId>org.jetbrains</groupId>
    <artifactId>annotations</artifactId>
</dependency>
```

Version is managed in parent POM: `24.0.1`

**Files using this:**
- `common-libs/src/main/java/com/sorted/common/utils/ValidationUtil.java`
- `common-libs/src/main/java/com/sorted/common/manage/otp/ManageOtp.java`
- `common-libs/src/main/java/com/sorted/common/helper/BaseMongoRepository.java`

---

### 2. Cannot find symbol errors

**Possible Causes:**
1. Missing dependency
2. Import statement pointing to wrong package
3. Module not built

**Solutions:**

#### Check imports are correct:
- Old: `import com.sorted.commons.*`
- New: `import com.sorted.common.*`

#### Rebuild modules in order:
```bash
# Build common-libs first
cd common-libs
mvn clean install

# Then build portal-service
cd ../portal-service
mvn clean install
```

#### Or build from root:
```bash
mvn clean install
```

---

### 3. Module not found errors

**Error:**
```
[ERROR] Failed to execute goal on project portal-service: 
Could not resolve dependencies for project com.sorted:portal-service:jar:0.0.1-SNAPSHOT: 
Could not find artifact com.sorted:common-libs:jar:0.0.1-SNAPSHOT
```

**Solution:**
Build common-libs first:
```bash
cd common-libs
mvn clean install
```

---

### 4. Import errors after migration

**Error:**
```
package com.sorted.commons does not exist
```

**Solution:**
All imports should use `com.sorted.common.*` (not `commons`). Run the import update script:

```powershell
Get-ChildItem -Path "portal-service\src" -Filter "*.java" -Recurse | ForEach-Object { 
    (Get-Content $_.FullName -Raw) -replace 'import com\.sorted\.commons\.', 'import com.sorted.common.' | 
    Set-Content $_.FullName -NoNewline 
}
```

---

### 5. MongoDB connection errors

**Error:**
```
com.mongodb.MongoTimeoutException: Timed out after 30000 ms while waiting to connect
```

**Solution:**
1. Ensure MongoDB is running
2. Check connection string in `portal-service/src/main/resources/application.properties`
3. Verify network connectivity to MongoDB

---

### 6. Spring Boot application won't start

**Possible Causes:**
1. Port already in use
2. Missing configuration
3. Bean creation errors

**Solutions:**

#### Check if port is in use:
```powershell
netstat -ano | findstr :8080
```

#### Check application.properties:
Ensure all required properties are set:
- MongoDB connection
- Server port
- Logging configuration

#### View detailed error logs:
```bash
mvn spring-boot:run -X
```

---

### 7. Lombok not working

**Error:**
```
cannot find symbol: method builder()
```

**Solution:**
1. Ensure Lombok plugin is installed in your IDE
2. Enable annotation processing in IDE settings
3. Rebuild project:
```bash
mvn clean install
```

---

### 8. JWT dependency issues

**Error:**
```
package io.jsonwebtoken does not exist
```

**Solution:**
JWT dependencies are in common-libs. Ensure you have:
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <scope>runtime</scope>
</dependency>
```

---

### 9. PDF generation dependencies missing

**Error:**
```
package com.lowagie.text does not exist
```

**Solution:**
OpenPDF dependency is required for PDF generation. It's already included in common-libs:
```xml
<dependency>
    <groupId>com.github.librepdf</groupId>
    <artifactId>openpdf</artifactId>
</dependency>
```

**Note:** OpenPDF is a fork of iText that uses the `com.lowagie.text` package.

---

### 10. Gson dependency missing

**Error:**
```
package com.google.gson does not exist
```

**Solution:**
Gson is included in common-libs for JSON processing:
```xml
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
</dependency>
```

**Files using this:**
- `InvoicePdfGenerator.java`
- Other utilities requiring JSON serialization/deserialization

---

### 11. PhonePe SDK not found

**Error:**
```
package com.phonepe does not exist
```

**Solution:**
Ensure the PhonePe repository is configured in parent POM:
```xml
<repository>
    <id>io.cloudrepo</id>
    <name>PhonePe JAVA SDK</name>
    <url>https://phonepe.mycloudrepo.io/public/repositories/phonepe-pg-sdk-java</url>
</repository>
```

---

### 12. Clean build from scratch

If all else fails, perform a complete clean build:

```bash
# Clean everything
mvn clean

# Delete target directories
Remove-Item -Recurse -Force common-libs\target
Remove-Item -Recurse -Force portal-service\target

# Clear local Maven cache (optional)
Remove-Item -Recurse -Force ~/.m2/repository/com/sorted

# Build from scratch
mvn clean install
```

---

## Build Commands Reference

### Build entire project
```bash
mvn clean install
```

### Build without tests
```bash
mvn clean install -DskipTests
```

### Build specific module
```bash
cd common-libs
mvn clean install
```

### Run application
```bash
cd portal-service
mvn spring-boot:run
```

### Run with specific profile
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Debug mode
```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

---

## IDE-Specific Issues

### IntelliJ IDEA

**Issue: Modules not recognized**
1. File → Invalidate Caches → Restart
2. Right-click on root pom.xml → Maven → Reload Project
3. File → Project Structure → Modules → Verify modules are present

**Issue: Lombok not working**
1. Install Lombok plugin
2. Settings → Build, Execution, Deployment → Compiler → Annotation Processors
3. Enable "Enable annotation processing"

### Eclipse

**Issue: Modules not imported**
1. File → Import → Existing Maven Projects
2. Select root directory
3. Ensure all modules are checked

**Issue: Lombok not working**
1. Download lombok.jar
2. Run: `java -jar lombok.jar`
3. Select Eclipse installation directory

### VS Code

**Issue: Java extension not recognizing modules**
1. Install "Java Extension Pack"
2. Reload window: Ctrl+Shift+P → "Reload Window"
3. Clean Java workspace: Ctrl+Shift+P → "Java: Clean Java Language Server Workspace"

---

## Dependency Version Reference

All versions are managed in parent POM:

| Dependency | Version | Property |
|------------|---------|----------|
| Java | 17 | java.version |
| Spring Boot | 3.2.0 | (parent) |
| Lombok | 1.18.30 | lombok.version |
| Guava | 32.1.3-jre | guava.version |
| PhonePe SDK | 2.1.0 | phonepe.version |
| Log4j | 2.20.0 | log4j.version |
| JWT | 0.11.5 | jjwt.version |
| JetBrains Annotations | 24.0.1 | jetbrains-annotations.version |
| OpenPDF | 1.3.30 | openpdf.version |
| Gson | 2.10.1 | gson.version |

---

## Getting Help

1. Check this troubleshooting guide
2. Review `MODULE_MIGRATION_SUMMARY.md`
3. Check `PROJECT_STRUCTURE.md`
4. Review Maven output for specific errors
5. Check application logs in `logs/` directory

---

## Useful Maven Commands

```bash
# Show dependency tree
mvn dependency:tree

# Show effective POM
mvn help:effective-pom

# Analyze dependencies
mvn dependency:analyze

# Update dependencies
mvn versions:display-dependency-updates

# Force update snapshots
mvn clean install -U

# Skip tests
mvn clean install -DskipTests

# Run specific test
mvn test -Dtest=TestClassName

# Package without running tests
mvn package -DskipTests

# Install to local repository
mvn install

# Deploy to remote repository
mvn deploy
```

---

Last Updated: 2026-02-28
