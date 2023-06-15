package app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

public class AppConfig {
    public static ServantInfo myServantInfo;
    public static SystemState systemState = new SystemState();

    public static void timestampedStandardPrint(String message) {
        DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        Date now = new Date();
        System.out.println(timeFormat.format(now) + " - " + message);
    }

    public static void timestampedErrorPrint(String message) {
        DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        Date now = new Date();
        System.err.println(timeFormat.format(now) + " - " + message);
    }

    public static int readIdFromProperties() {
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream("nodes.properties"));
            int id = Integer.parseInt(properties.getProperty("servant_count"));
            properties.setProperty("servant_count", String.valueOf(id+1));
            FileOutputStream fileOutputStream = new FileOutputStream("nodes.properties");
            properties.store(fileOutputStream, null);
            return id;
        } catch (IOException e) {
            e.printStackTrace();
            timestampedErrorPrint("Couldn't open properties file. Exiting...");
            System.exit(0);
            return -1;
        }
    }

    public static String readRootDirFromProperties() {
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream("nodes.properties"));
            String rootDir = properties.getProperty("rootDir");

            File folder = new File(rootDir + "\\root" + myServantInfo.getId());

            // Create the folder
            boolean created = folder.mkdir();

            if (created) {
                return rootDir + "\\root" + myServantInfo.getId();
            } else {
                System.out.println("FAILED TO CREATE ROOT DIR");
                return "";
            }
        } catch (IOException e) {
            e.printStackTrace();
            timestampedErrorPrint("Couldn't open properties file. Exiting...");
            System.exit(0);
            return "";
        }
    }
}
