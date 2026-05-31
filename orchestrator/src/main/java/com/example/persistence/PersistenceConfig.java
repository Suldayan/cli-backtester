package com.example.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.JournalMode;
import org.sqlite.SQLiteConfig.SynchronousMode;
import org.sqlite.SQLiteConfig.TempStore;

import javax.sql.DataSource;

@Configuration
public class PersistenceConfig {

    @Bean
    public DataSource dataSource() {
        final SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.setJournalMode(JournalMode.WAL);
        sqliteConfig.setSynchronous(SynchronousMode.NORMAL);
        sqliteConfig.enforceForeignKeys(true);
        sqliteConfig.setCacheSize(64_000);
        sqliteConfig.setTempStore(TempStore.MEMORY);

        final HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:sqlite:backtester.db");
        hikari.setMaximumPoolSize(1);
        hikari.setDataSourceProperties(sqliteConfig.toProperties());
        return new HikariDataSource(hikari);
    }
}