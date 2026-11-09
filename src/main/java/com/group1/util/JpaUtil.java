package com.group1.util;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

/**
 * Standard JPA Utility providing thread-safe EntityManagerFactory
 * backed by Hibernate ORM provider.
 */
public class JpaUtil {
    private static EntityManagerFactory emf;

    static {
        try {
            emf = Persistence.createEntityManagerFactory("AITA_PU");
        } catch (Throwable ex) {
            System.err.println("[JPA/Hibernate] Initial EntityManagerFactory creation failed: " + ex.getMessage());
        }
    }

    public static EntityManager getEntityManager() {
        if (emf == null || !emf.isOpen()) {
            emf = Persistence.createEntityManagerFactory("AITA_PU");
        }
        return emf.createEntityManager();
    }

    public static EntityManagerFactory getEntityManagerFactory() {
        return emf;
    }

    public static void shutdown() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }
}
