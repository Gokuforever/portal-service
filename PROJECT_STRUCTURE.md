# Portal Service - Project Structure

## Module Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     portal-parent (POM)                      │
│                                                               │
│  • Manages all modules                                       │
│  • Centralized dependency management                         │
│  • Common build configuration                                │
└───────────────────┬─────────────────────────────────────────┘
                    │
        ┌───────────┴───────────┐
        │                       │
        ▼                       ▼
┌──────────────┐        ┌──────────────────┐
│ common-libs  │◄───────│ portal-service   │
│   (JAR)      │        │     (JAR)        │
└──────────────┘        └──────────────────┘
```

## Dependency Flow

```
portal-service
    │
    ├─► common-libs (internal)
    │       │
    │       ├─► Spring Boot Starter Web
    │       ├─► Spring Data MongoDB
    │       ├─► Spring Security
    │       ├─► JWT Libraries
    │       ├─► Guava
    │       ├─► Lombok
    │       └─► Apache Commons
    │
    ├─► Spring Boot Web Services
    ├─► Spring Boot DevTools
    ├─► PhonePe SDK
    └─► Spring Boot Test
```

## Directory Structure

```
portal-service/
│
├── pom.xml                                 # Parent POM
├── .mvn/                                   # Maven wrapper
├── mvnw, mvnw.cmd                          # Maven wrapper scripts
├── .git/                                   # Git repository
├── .gitignore
│
├── MODULE_MIGRATION_SUMMARY.md             # Migration summary
├── MIGRATION_GUIDE.md                      # Migration guide
├── README-MODULES.md                       # Module documentation
├── PROJECT_STRUCTURE.md                    # This file
│
├── common-libs/                            # Shared library module
│   ├── pom.xml                             # Module POM
│   ├── README.md                           # Module documentation
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   └── com/sorted/common/
│       │   │       ├── beans/              # Data transfer objects
│       │   │       ├── config/             # Configuration classes
│       │   │       ├── constants/          # Application constants
│       │   │       ├── entity/             # Entity classes
│       │   │       │   ├── mongo/          # MongoDB entities
│       │   │       │   ├── beans/          # Entity beans
│       │   │       │   └── service/        # Entity services
│       │   │       ├── enums/              # Enumerations
│       │   │       ├── exceptions/         # Custom exceptions
│       │   │       ├── helper/             # Helper classes
│       │   │       ├── jwt/                # JWT utilities
│       │   │       ├── manage/             # Management utilities
│       │   │       ├── notifications/      # Notification handlers
│       │   │       ├── porter/             # Porter integration
│       │   │       ├── repository/         # Repository interfaces
│       │   │       ├── service/            # Common services
│       │   │       └── utils/              # Utility classes
│       │   └── resources/
│       └── test/
│           └── java/
│
└── portal-service/                         # Main service module
    ├── pom.xml                             # Module POM
    ├── Dockerfile                          # Docker configuration
    ├── docker-compose.yml                  # Docker Compose config
    └── src/
        ├── main/
        │   ├── java/
        │   │   └── com/sorted/portal/
        │   │       ├── PortalApplication.java
        │   │       ├── aspect/             # AOP aspects
        │   │       ├── assisting/          # Assisting classes
        │   │       ├── bl_services/        # Business logic services
        │   │       ├── config/             # Configuration
        │   │       ├── controller/         # REST controllers
        │   │       ├── crons/              # Scheduled jobs
        │   │       ├── enums/              # Service enums
        │   │       ├── PhonePe/            # PhonePe integration
        │   │       ├── porter/             # Porter integration
        │   │       ├── razorpay/           # Razorpay integration
        │   │       ├── request/            # Request beans
        │   │       ├── response/           # Response beans
        │   │       ├── service/            # Service layer
        │   │       ├── test/               # Test utilities
        │   │       └── webhooks/           # Webhook handlers
        │   └── resources/
        │       ├── application.properties
        │       └── static/
        └── test/
            └── java/
```

## Package Organization

### common-libs Packages

| Package | Purpose | Key Classes |
|---------|---------|-------------|
| `beans` | Data transfer objects | BuyerInfo, CouponUsage, InvoiceItem, PaymentInfo |
| `config` | Configuration classes | MongoConfig, MongoIndexConfig, StaticMongoAccessor |
| `constants` | Application constants | CommonConstants |
| `entity.mongo` | MongoDB entities | Address, Cart, Category_Master, CouponEntity, Order_Details, Product, Users |
| `entity.service` | Entity services | Cart_Service, CouponService, ProductService, Users_Service |
| `enums` | Enumerations | Activity, Permission, ResponseCode, UserType, OrderStatus |
| `exceptions` | Custom exceptions | AccessDeniedException, BusinessException, CustomIllegalArgumentsException |
| `helper` | Helper utilities | AggregationFilter, BaseMongoRepository, SERequest |
| `jwt` | JWT utilities | JWT token handling |
| `repository` | Repository interfaces | MongoDB repositories |
| `service` | Common services | ZoneHandlerService |
| `utils` | Utility classes | DateUtils, GeoUtils, StringUtils, ValidationUtils |

### portal-service Packages

| Package | Purpose | Key Classes |
|---------|---------|-------------|
| `aspect` | AOP aspects | RateLimiterAspect |
| `bl_services` | Business logic | ManageCart_BLService, ManageOrder_BLService, ManageProduct_BLService |
| `config` | Configuration | RateLimiterConfig, SecurityConfig |
| `controller` | REST controllers | CartController, OrderController, ProductController |
| `enums` | Service enums | CartAction, OrderProperties, ProductProperties |
| `PhonePe` | PhonePe integration | PhonePeUtility |
| `razorpay` | Razorpay integration | RazorpayUtility |
| `service` | Service layer | SecureReturnService, StoreProductService |
| `webhooks` | Webhook handlers | PorterWebhookController |

## Build Lifecycle

```
1. Parent POM (portal-parent)
   ↓
2. common-libs module
   ├─ Compile
   ├─ Test
   ├─ Package (JAR)
   └─ Install to local Maven repo
   ↓
3. portal-service module
   ├─ Resolve common-libs dependency
   ├─ Compile
   ├─ Test
   ├─ Package (JAR)
   └─ Install to local Maven repo
```

## Key Files

### Root Level
- **pom.xml**: Parent POM managing all modules
- **MODULE_MIGRATION_SUMMARY.md**: Summary of migration changes
- **MIGRATION_GUIDE.md**: Step-by-step migration instructions
- **README-MODULES.md**: Complete module documentation

### common-libs
- **pom.xml**: Module configuration with all shared dependencies
- **README.md**: Module-specific documentation

### portal-service
- **pom.xml**: Service configuration depending on common-libs
- **Dockerfile**: Container configuration
- **docker-compose.yml**: Multi-container setup
- **application.properties**: Application configuration

## Development Workflow

### Adding New Common Code
1. Add code to `common-libs/src/main/java/com/sorted/common/`
2. Build common-libs: `cd common-libs && mvn clean install`
3. Use in portal-service with import: `import com.sorted.common.*`

### Adding New Service Code
1. Add code to `portal-service/src/main/java/com/sorted/portal/`
2. Build portal-service: `cd portal-service && mvn clean package`

### Building Everything
```bash
# From root directory
mvn clean install
```

## Module Dependencies

```
common-libs dependencies:
  ├─ Spring Boot Starter (core)
  ├─ Spring Boot Starter Web
  ├─ Spring Data MongoDB
  ├─ Spring Security
  ├─ JWT (jjwt-api, jjwt-impl, jjwt-jackson)
  ├─ Guava
  ├─ Lombok
  ├─ Log4j API
  ├─ Spring AOP
  ├─ Jackson Databind
  ├─ Apache Commons Lang3
  ├─ Apache HttpComponents Client5
  └─ Spring Boot Test (test scope)

portal-service dependencies:
  ├─ common-libs (internal)
  ├─ Spring Boot Web Services
  ├─ Spring Boot DevTools (runtime)
  ├─ Spring Boot AOP
  ├─ Lombok
  ├─ Log4j API
  ├─ Guava
  ├─ PhonePe SDK
  └─ Spring Boot Test (test scope)
```

## Version Management

All dependency versions are managed in the parent POM:

```xml
<properties>
    <java.version>17</java.version>
    <lombok.version>1.18.30</lombok.version>
    <guava.version>32.1.3-jre</guava.version>
    <phonepe.version>2.1.0</phonepe.version>
    <log4j.version>2.20.0</log4j.version>
    <jjwt.version>0.11.5</jjwt.version>
</properties>
```

## Benefits of This Structure

1. **Modularity**: Clear separation of concerns
2. **Reusability**: Common code can be shared across multiple services
3. **Maintainability**: Easier to manage and update dependencies
4. **Build Optimization**: Maven can build modules in parallel
5. **Scalability**: Easy to add new modules (e.g., admin-service, api-gateway)
6. **No External Dependencies**: All code is self-contained
7. **Version Control**: Single source of truth for all versions

## Future Expansion

Potential new modules to add:
- `admin-service`: Admin panel backend
- `api-gateway`: API gateway for routing
- `notification-service`: Centralized notifications
- `common-models`: Shared data models
- `common-security`: Security utilities
