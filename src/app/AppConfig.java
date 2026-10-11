package app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class AppConfig {
    private static final String PROPERTIES_FILE = "nodes.properties";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Logger LOGGER = createLogger();
    // Adresa na kojoj se vrte bootstrap i svi cvorovi sistema. Cita se iz properties fajla ("host"), podrazumevano localhost.
    public static final String HOST = readHostFromProperties();
    public static ServantInfo myServantInfo;
    public static SystemState systemState = new SystemState();

    private AppConfig() {
    }

    private static Logger createLogger() {
        Logger logger = Logger.getLogger(AppConfig.class.getName());
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord logRecord) {
                return LocalTime.now().format(TIME_FORMAT) + " - " + logRecord.getMessage() + System.lineSeparator();
            }
        });
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        return logger;
    }

    private static Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        try (FileInputStream fileInputStream = new FileInputStream(PROPERTIES_FILE)) {
            properties.load(fileInputStream);
        }
        return properties;
    }

    private static String readHostFromProperties() {
        try {
            return loadProperties().getProperty("host", "localhost");
        } catch (IOException e) {
            return "localhost";
        }
    }

    public static void refreshMyServantInfo() {
        myServantInfo = systemState.getInfoById(myServantInfo.getId());
    }

    public static void applySystemState(SystemState newSystemState) {
        systemState = newSystemState;
        refreshMyServantInfo();
    }

    public static void timestampedStandardPrint(String message) {
        LOGGER.info(message);
    }

    public static void timestampedErrorPrint(String message) {
        LOGGER.severe(message);
    }

    public static int readIdFromProperties() {
        try {
            Properties properties = loadProperties();
            int id = Integer.parseInt(properties.getProperty("servant_count"));
            properties.setProperty("servant_count", String.valueOf(id + 1));
            try (FileOutputStream fileOutputStream = new FileOutputStream(PROPERTIES_FILE)) {
                properties.store(fileOutputStream, null);
            }
            return id;
        } catch (IOException e) {
            timestampedErrorPrint("Couldn't open properties file (" + e + "). Exiting...");
            System.exit(0);
            return -1;
        }
    }

    public static String readRootDirFromProperties() {
        try {
            String rootDir = loadProperties().getProperty("rootDir");

            File folder = new File(rootDir + "\\root" + myServantInfo.getId());

            // Create the folder
            boolean created = folder.mkdir();

            if (created) {
                return rootDir + "\\root" + myServantInfo.getId();
            } else {
                timestampedErrorPrint("FAILED TO CREATE ROOT DIR");
                return "";
            }
        } catch (IOException e) {
            timestampedErrorPrint("Couldn't open properties file (" + e + "). Exiting...");
            System.exit(0);
            return "";
        }
    }
}
