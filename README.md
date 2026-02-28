# Portal Service - Multi-Module Project

A Spring Boot-based portal service organized as a multi-module Maven project.

## 🏗️ Project Structure

This project consists of two main modules:

- **common-libs**: Shared library containing common utilities, entities, and services
- **portal-service**: Main application service that depends on common-libs

```
portal-service/
├── common-libs/          # Shared library module
└── portal-service/       # Main service module
```

## 📋 Prerequisites

- Java 17 or higher
- Maven 3.6+
- MongoDB (for runtime)

## 🚀 Quick Start

### Build the Project

```bash
# Build all modules
mvn clean install

# Skip tests
mvn clean install -DskipTests
```

### Run the Application

```bash
# Using Maven
cd portal-service
mvn spring-boot:run

# Using JAR
cd portal-service/target
java -jar portal-service-0.0.1-SNAPSHOT.jar
```

### Run with Docker

```bash
cd portal-service
docker-compose up
```

## 📦 Modules

### common-libs

Shared library containing:
- MongoDB entities and repositories
- Common beans and DTOs
- Utility classes
- Exception handlers
- Configuration classes
- JWT utilities
- Service interfaces

**Package**: `com.sorted.common.*`

### portal-service

Main application service containing:
- REST controllers
- Business logic services
- Request/Response beans
- Payment integrations (PhonePe, Razorpay)
- Webhook handlers
- Scheduled jobs

**Package**: `com.sorted.portal.*`

## 🔧 Configuration

Configuration is managed through `portal-service/src/main/resources/application.properties`:

- MongoDB connection settings
- Rate limiting configuration
- Logging levels
- Payment gateway credentials (PhonePe, Razorpay)

## 📚 Documentation

- **[MODULE_MIGRATION_SUMMARY.md](MODULE_MIGRATION_SUMMARY.md)**: Summary of migration from single to multi-module structure
- **[MIGRATION_GUIDE.md](MIGRATION_GUIDE.md)**: Detailed migration instructions
- **[README-MODULES.md](README-MODULES.md)**: Complete module documentation
- **[PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md)**: Detailed project structure and architecture
- **[common-libs/README.md](common-libs/README.md)**: Common libraries documentation

## 🛠️ Development

### Adding New Features

#### To common-libs:
```bash
# 1. Add code to common-libs/src/main/java/com/sorted/common/
# 2. Build the module
cd common-libs
mvn clean install
```

#### To portal-service:
```bash
# 1. Add code to portal-service/src/main/java/com/sorted/portal/
# 2. Import from common-libs: import com.sorted.common.*
# 3. Build the module
cd portal-service
mvn clean package
```

### Running Tests

```bash
# Run all tests
mvn test

# Run tests for specific module
cd portal-service
mvn test
```

## 🔑 Key Features

- ✅ Multi-module Maven structure
- ✅ Shared common library
- ✅ Spring Boot 3.2.0
- ✅ MongoDB integration
- ✅ JWT authentication
- ✅ Rate limiting
- ✅ Payment gateway integration (PhonePe, Razorpay)
- ✅ Porter webhook integration
- ✅ Comprehensive logging
- ✅ Docker support

## 📊 Technology Stack

- **Framework**: Spring Boot 3.2.0
- **Language**: Java 17
- **Database**: MongoDB
- **Build Tool**: Maven
- **Security**: Spring Security + JWT
- **Payment**: PhonePe SDK, Razorpay
- **Utilities**: Guava, Apache Commons, Lombok
- **Containerization**: Docker

## 🔐 Security

- JWT-based authentication
- Spring Security integration
- Rate limiting (5 requests per minute)
- Secure payment processing

## 📝 API Documentation

The service exposes REST APIs for:
- User management
- Product catalog
- Shopping cart
- Order processing
- Payment processing
- Coupon management
- Feedback and reviews
- Secure returns

## 🐳 Docker

Build and run using Docker:

```bash
# Build image
cd portal-service
docker build -t portal-service:latest .

# Run with docker-compose
docker-compose up -d
```

## 🧪 Testing

```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# Skip tests during build
mvn clean install -DskipTests
```

## 📈 Build Status

To check build status:
```bash
mvn clean verify
```

## 🤝 Contributing

1. Create a feature branch
2. Make your changes
3. Run tests: `mvn test`
4. Build the project: `mvn clean install`
5. Submit a pull request

## 📄 License

[Add your license information here]

## 👥 Team

Sorted Team

## 📞 Support

For issues or questions:
1. Check the documentation in the `docs/` folder
2. Review module-specific README files
3. Contact the development team

## 🔄 Migration Notes

This project was migrated from a single-module structure to a multi-module structure. The `se-commons` external dependency has been internalized into the `common-libs` module.

**Key Changes**:
- ❌ Removed `se-commons` dependency
- ✅ Created `common-libs` module with all common code
- ✅ Updated all imports from `com.sorted.commons.*` to `com.sorted.common.*`
- ✅ Centralized dependency management in parent POM

See [MODULE_MIGRATION_SUMMARY.md](MODULE_MIGRATION_SUMMARY.md) for complete details.

## 🎯 Next Steps

1. Build the project: `mvn clean install`
2. Run tests: `mvn test`
3. Start the application: `cd portal-service && mvn spring-boot:run`
4. Access the API at `http://localhost:8080`

---

**Version**: 0.0.1-SNAPSHOT  
**Last Updated**: 2026-02-28
