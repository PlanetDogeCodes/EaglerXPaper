/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import net.lax1dude.eaglercraft.backend.util.ILoggerAdapter;

public class EaglerDrivers {
    private static final Map<String, ClassLoader> driversJARs = new HashMap<String, ClassLoader>();
    private static final Map<String, Driver> driversDrivers = new HashMap<String, Driver>();

    private static Driver initializeDriver(String address, String driverClass, File baseFolder, ILoggerAdapter logger) {
        Class<?> loadedDriver;
        ClassLoader classLoader;
        if (address.equalsIgnoreCase("classpath")) {
            classLoader = EaglerDrivers.class.getClassLoader();
        } else {
            classLoader = driversJARs.get(address);
            if (classLoader == null) {
                URL driverURL;
                File driver;
                if (address.equalsIgnoreCase("internal")) {
                    driver = new File(baseFolder, "drivers/sqlite-jdbc.jar");
                    driver.getParentFile().mkdirs();
                    if (!driver.isFile()) {
                        try (InputStream is = EaglerDrivers.class.getResourceAsStream("/net/lax1dude/eaglercraft/backend/skin_cache/libs/sqlite-jdbc.library");){
                            if (is == null) {
                                throw new IOException("Missing classpath resource: net/lax1dude/eaglercraft/backend/skin_cache/libs/sqlite-jdbc.library");
                            }
                            try (FileOutputStream os = new FileOutputStream(driver);){
                                int n;
                                byte[] buf = new byte[8192];
                                while ((n = is.read(buf)) != -1) {
                                    ((OutputStream)os).write(buf, 0, n);
                                }
                            }
                        }
                        catch (IOException ex) {
                            throw new ExceptionInInitializerError(ex);
                        }
                    }
                } else {
                    driver = new File(address);
                }
                try {
                    driverURL = driver.toURI().toURL();
                }
                catch (MalformedURLException ex) {
                    logger.error("Invalid JDBC driver path: " + address);
                    throw new ExceptionInInitializerError(ex);
                }
                classLoader = URLClassLoader.newInstance(new URL[]{driverURL}, ClassLoader.getSystemClassLoader());
                driversJARs.put(address, classLoader);
            }
        }
        try {
            loadedDriver = classLoader.loadClass(driverClass);
        }
        catch (ClassNotFoundException ex) {
            logger.error("Could not find JDBC driver class: " + driverClass);
            throw new ExceptionInInitializerError(ex);
        }
        Driver sqlDriver = null;
        try {
            sqlDriver = (Driver)loadedDriver.getConstructor(new Class[0]).newInstance(new Object[0]);
        }
        catch (Throwable ex) {
            logger.error("Could not initialize JDBC driver class: " + driverClass);
            throw new ExceptionInInitializerError(ex);
        }
        return sqlDriver;
    }

    public static Connection[] connectToDatabase(String address, String driverClass, String driverPath, Properties props, File baseFolder, ILoggerAdapter logger, int count) throws SQLException {
        Connection[] arr;
        block12: {
            if (driverClass.equalsIgnoreCase("internal")) {
                driverClass = "org.sqlite.JDBC";
            }
            arr = new Connection[count];
            try {
                if (driverPath == null) {
                    try {
                        Class.forName(driverClass);
                    }
                    catch (ClassNotFoundException e) {
                        throw new SQLException("Driver class not found in JRE: " + driverClass, e);
                    }
                    for (int i = 0; i < count; ++i) {
                        arr[i] = DriverManager.getConnection(address, props);
                    }
                    break block12;
                }
                String driverMapPath = "" + driverPath + "?" + driverClass;
                Driver dv = driversDrivers.get(driverMapPath);
                if (dv == null) {
                    dv = EaglerDrivers.initializeDriver(driverPath, driverClass, baseFolder, logger);
                    driversDrivers.put(driverMapPath, dv);
                }
                for (int i = 0; i < count; ++i) {
                    arr[i] = dv.connect(address, props);
                }
            }
            catch (SQLException ex) {
                for (int i = 0; i < count; ++i) {
                    if (arr[i] == null) continue;
                    try {
                        arr[i].close();
                        continue;
                    }
                    catch (SQLException exx) {
                        ex.addSuppressed(exx);
                    }
                }
                throw ex;
            }
        }
        return arr;
    }
}

