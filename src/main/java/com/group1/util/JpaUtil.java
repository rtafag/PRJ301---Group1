package com.group1.util;

import java.util.HashMap;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

/**
 * Standard JPA Utility providing thread-safe EntityManagerFactory
 * backed by Hibernate ORM provider.
 */
public class JpaUtil {
    private static volatile EntityManagerFactory emf;

    public static EntityManager getEntityManager() {
        return getEntityManagerFactory().createEntityManager();
    }

    public static EntityManagerFactory getEntityManagerFactory() {
        EntityManagerFactory factory = emf;
        if (factory == null || !factory.isOpen()) {
            synchronized (JpaUtil.class) {
                factory = emf;
                if (factory == null || !factory.isOpen()) {
                    factory = createEntityManagerFactory();
                    emf = factory;
                }
            }
        }
        return factory;
    }

    public static void shutdown() {
        synchronized (JpaUtil.class) {
            if (emf != null && emf.isOpen()) {
                emf.close();
            }
            emf = null;
        }
    }

    private static EntityManagerFactory createEntityManagerFactory() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("javax.persistence.jdbc.url", System.getenv().getOrDefault(
                "DB_URL",
                "jdbc:sqlserver://localhost:1433;databaseName=AITA_DB;encrypt=false;trustServerCertificate=true;"));
        properties.put("javax.persistence.jdbc.user", getEnvOrDefault("DB_USER", "sa"));
        properties.put("javax.persistence.jdbc.password", getEnvOrDefault("DB_PASSWORD", "password123"));
        return Persistence.createEntityManagerFactory("AITA_PU", properties);
    }

    private static String getEnvOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value;
    }
}
