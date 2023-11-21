package me.kavin.piped.utils;

import liquibase.Liquibase;
import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionCommandStep;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import me.kavin.piped.consts.Constants;

import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;

public class LiquibaseHelper {

    public static void init() throws Exception {

        String url = Constants.hibernateProperties.get("hibernate.connection.url");
        String username = Constants.hibernateProperties.get("hibernate.connection.username");
        String password = Constants.hibernateProperties.get("hibernate.connection.password");

        // ensure postgres driver is loaded
        DriverManager.registerDriver(new org.postgresql.Driver());

        // register YugabyteDB database
        DatabaseFactory.getInstance().register(new liquibase.ext.yugabytedb.database.YugabyteDBDatabase());

        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(DriverManager.getConnection(url, username, password)));

        try (Liquibase liquibase = new Liquibase("changelog/db.changelog-master.xml", new ClassLoaderResourceAccessor(), database)) {

            Map<String, Object> scopeObjects = new HashMap<>();
            scopeObjects.put(Scope.Attr.database.name(), liquibase.getDatabase());
            scopeObjects.put(Scope.Attr.resourceAccessor.name(), liquibase.getResourceAccessor());

            Scope.child(scopeObjects, () -> {
                CommandScope updateCommand = new CommandScope(UpdateCommandStep.COMMAND_NAME);
                updateCommand.addArgumentValue(DbUrlConnectionCommandStep.DATABASE_ARG, liquibase.getDatabase());
                updateCommand.addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, liquibase.getChangeLogFile());
                updateCommand.execute();
            });

        }
    }

}
