# Library Management System

A comprehensive library management system built with Java, featuring a JavaFX desktop client and a Socket-based server. The system provides complete book management, borrowing operations, user management, AI-powered recommendations, and real-time chat functionality.

## Features

- **Book Management**: Add, update, delete, and search books with ISBN, title, author, category
- **Borrowing System**: Borrow and return books with automatic due date tracking
- **User Management**: Support for both regular users and administrators
- **AI Recommendations**: Intelligent book recommendations based on user preferences and reading history
- **Real-time Chat**: User-to-user messaging system
- **Fine Management**: Automatic fine calculation with configurable rate tiers
- **Statistics Dashboard**: Comprehensive statistics for administrators
- **Trending Books**: Display popular books based on borrowing frequency

## Prerequisites

- **Java**: JDK 17 or higher
- **Maven**: 3.6 or higher
- **PostgreSQL**: 12 or higher
- **pgvector Extension** (optional): For enhanced AI recommendation features

## Installation

### 1. Database Setup

1. Install PostgreSQL and create a database:
```sql
CREATE DATABASE library_db;
```

2. Execute the database schema:
```bash
psql -U postgres -d library_db -f database/schema.sql
```

3. Initialize admin user (optional):
```bash
psql -U postgres -d library_db -f database/init_admin.sql
```

4. Import demo data (optional):
```bash
psql -U postgres -d library_db -f database/import_demo_data.sql
```

### 2. Configuration

Edit `server/src/main/resources/application.properties` to configure database connection:

```properties
# Database Configuration
db.url=jdbc:postgresql://localhost:5432/library_db
db.username=postgres
db.password=your_password
db.pool.size.minimum=5
db.pool.size.maximum=20

# Server Configuration
server.port=9090
server.threadPoolSize=20
```

### 3. Build Project

```bash
mvn clean compile
```

Or use the provided batch script (Windows):
```bash
完整部署.bat
```

## Usage

### Starting the Server

**Option 1: Using batch script (Windows)**
```bash
start-server.bat
```

**Option 2: Using Maven**
```bash
mvn -pl server compile exec:java -Dexec.mainClass="com.library.server.ServerMain" -Dexec.args="9090"
```

**Option 3: Using command line arguments**
```bash
java -cp target/classes com.library.server.ServerMain [port] [threadPoolSize]
```

The server will start on port 9090 by default (or the port specified in `application.properties`).

### Starting the Client

**Option 1: Using batch script (Windows)**
```bash
start-client.bat
```

**Option 2: Using Maven**
```bash
mvn -pl client compile javafx:run
```

### Default Accounts

After running `init_admin.sql`, you can login with:
- **Admin**: username and password as configured in the SQL script
- **Demo Users**: Use `create_demo_users.sql` to create test accounts

### User Operations

**Regular Users can:**
- Search and browse books
- Borrow and return books
- View borrowing history
- Receive AI-powered book recommendations
- Chat with other users
- View personal fines

**Administrators can:**
- Manage books (add, update, delete, import from CSV)
- View all borrowing records
- Manage users (freeze/unfreeze accounts)
- Configure fine rate tiers
- View system statistics
- Send reminders to users
- Manage all user fines

## Project Structure

```
library-system/
├── client/                 # JavaFX client application
│   ├── src/main/java/     # Client source code
│   └── src/main/resources/ # UI resources (CSS, images)
├── server/                 # Socket server application
│   ├── src/main/java/     # Server source code
│   └── src/main/resources/ # Configuration files
├── common/                 # Shared code between client and server
│   └── src/main/java/     # Common protocols and utilities
├── database/               # SQL scripts
│   ├── schema.sql         # Main database schema
│   ├── init_admin.sql     # Admin user initialization
│   └── *.sql              # Additional migration scripts
└── pom.xml                 # Maven parent POM
```

## Technical Details

### Architecture

- **Client-Server Architecture**: Socket-based communication using TCP
- **Protocol**: Custom JSON-based protocol for request/response
- **Database**: PostgreSQL with connection pooling (HikariCP)
- **UI Framework**: JavaFX 17
- **Build Tool**: Maven 3.x

### Technology Stack

- **Backend**:
  - Java 17
  - PostgreSQL 12+
  - HikariCP (Connection Pooling)
  - Jackson (JSON Processing)
  - SLF4J + Logback (Logging)

- **Frontend**:
  - JavaFX 17
  - CSS for styling

- **AI/ML Features**:
  - Vector embeddings for book recommendations
  - Graph-based recommendation algorithms
  - pgvector extension support (optional)

### Key Components

1. **Protocol Layer** (`common/protocol/`):
   - `OpCode`: Operation codes for all system operations
   - `Request/Response`: JSON-based communication protocol
   - `ErrorCode`: Standardized error handling

2. **Server Services** (`server/service/`):
   - `UserService`: User authentication and management
   - `BookService`: Book CRUD operations
   - `BorrowService`: Borrowing and returning logic
   - `AIRecommendService`: AI-powered recommendations
   - `ChatService`: Real-time messaging
   - `FineService`: Fine calculation and management

3. **Data Access Layer** (`server/dao/`):
   - DAO pattern for database operations
   - Connection pooling with HikariCP
   - Prepared statements for SQL injection prevention

4. **Client Views** (`client/view/`):
   - `LoginView`: User authentication
   - `UserHomeView`: Regular user dashboard
   - `AdminHomeView`: Administrator dashboard
   - `ChatView`: Messaging interface
   - `ReaderDashboardView`: Reading recommendations

### Database Schema

- **users**: User accounts with roles (USER/ADMIN) and status (ACTIVE/FROZEN)
- **books**: Book information with inventory tracking
- **borrow_records**: Borrowing history and status
- **messages**: User-to-user chat messages
- **book_embeddings**: Vector embeddings for AI recommendations (optional)
- **fine_rate_config**: Configurable fine rate tiers

### Security Features

- Password hashing (stored as hash, never plain text)
- SQL injection prevention (PreparedStatement)
- User role-based access control
- Account freeze/unfreeze functionality

### Performance Optimizations

- Database connection pooling (HikariCP)
- Indexed database queries
- Thread pool for concurrent client connections
- Efficient JSON serialization (Jackson)

## Development

### Building from Source

```bash
# Clean and compile all modules
mvn clean compile

# Package as JAR files
mvn clean package

# Run tests
mvn test
```

### Running Tests

```bash
mvn test
```

### Code Style

- Follow Java naming conventions
- Use meaningful variable and method names
- Add JavaDoc comments for public APIs
- Maintain consistent indentation (4 spaces)

## Troubleshooting

### Server won't start

1. Check if port 9090 is already in use:
```bash
netstat -ano | findstr :9090
```

2. Verify database connection settings in `application.properties`

3. Ensure PostgreSQL is running and database exists

### Client can't connect to server

1. Verify server is running and listening on the correct port
2. Check firewall settings
3. Verify server address in client code (default: localhost:9090)

### Database connection errors

1. Verify PostgreSQL is running
2. Check database credentials in `application.properties`
3. Ensure database `library_db` exists
4. Verify user has proper permissions

### Encoding issues

The project uses UTF-8 encoding. If you encounter encoding problems:
- Ensure your terminal supports UTF-8
- Check file encoding settings in your IDE
- Windows batch scripts use `chcp 65001` to set UTF-8

## License

This project is for educational purposes.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## Contact

For questions or issues, please open an issue on GitHub.

