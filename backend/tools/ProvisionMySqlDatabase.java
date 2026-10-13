import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

/** Minimal one-time utility for creating an isolated QMS MySQL database. */
public final class ProvisionMySqlDatabase {
    private ProvisionMySqlDatabase() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !args[1].matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("Usage: ProvisionMySqlDatabase <admin-jdbc-url> <database_name>");
        }
        String userName = requiredEnvironment("QMS_PROVISION_USER");
        String password = requiredEnvironment("QMS_PROVISION_PASSWORD");
        String databaseName = args[1];

        try (Connection connection = DriverManager.getConnection(args[0], userName, password)) {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.schemata WHERE schema_name = ?")) {
                statement.setString(1, databaseName);
                if (statement.executeQuery().next()) {
                    System.out.println("DATABASE_EXISTS " + databaseName);
                    return;
                }
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE `" + databaseName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            }
            System.out.println("DATABASE_CREATED " + databaseName);
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + name);
        }
        return value;
    }
}
