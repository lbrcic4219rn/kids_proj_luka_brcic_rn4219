package app;

import failure.FailureDetector;
import file.FileIndex;
import file.FileService;
import file.Storage;
import file.WorkDir;
import lombok.Getter;
import lombok.Setter;
import mutex.SuzukiKasamiDistributedMutex;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class AppConfig {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Logger LOGGER = createLogger();

    private static ServantInfo me;
    private static ServantInfo bootstrap;
    private static long weakLimit = 1000;
    private static long strongLimit = 10000;
    private static int teamLimit = 2;

    private static SystemState state;
    private static FileIndex index;
    private static Storage storage;
    private static WorkDir workDir;
    private static SuzukiKasamiDistributedMutex mutex;
    private static FileService files;
    private static FailureDetector detector;

    @Getter
    @Setter
    private static volatile boolean joined;
    @Getter
    @Setter
    private static volatile boolean quitting;

    private AppConfig() {
    }

    private static Logger createLogger() {
        Logger logger = Logger.getLogger(AppConfig.class.getName());
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord logRecord) {
                StringBuilder line = new StringBuilder(LocalTime.now(ZoneId.systemDefault()).format(TIME_FORMAT))
                        .append(" - ").append(logRecord.getMessage()).append(System.lineSeparator());
                if (logRecord.getThrown() != null) {
                    StringWriter trace = new StringWriter();
                    logRecord.getThrown().printStackTrace(new PrintWriter(trace));
                    line.append(trace);
                }
                return line.toString();
            }
        });
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        return logger;
    }

    public static void initBootstrap(String ip, int port) {
        me = new ServantInfo(ip, port, "", 0);
    }

    public static void initServant(String configPath) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(Path.of(configPath))) {
            properties.load(input);
        }
        String team = required(properties, "team");
        if (!team.matches("[a-z]+")) {
            throw new IllegalArgumentException("Team must consist of letters a-z, got: " + team);
        }
        me = new ServantInfo(properties.getProperty("ip", "localhost"), Integer.parseInt(required(properties, "port")),
                team, System.currentTimeMillis());
        bootstrap = new ServantInfo(required(properties, "bootstrap_ip"),
                Integer.parseInt(required(properties, "bootstrap_port")), "", 0);
        weakLimit = Long.parseLong(properties.getProperty("weak_limit", "1000"));
        strongLimit = Long.parseLong(properties.getProperty("strong_limit", "10000"));
        teamLimit = Integer.parseInt(properties.getProperty("limit", "2"));

        state = new SystemState();
        index = new FileIndex();
        storage = new Storage(Path.of(required(properties, "storage")));
        workDir = new WorkDir(Path.of(required(properties, "work_root")));
        mutex = new SuzukiKasamiDistributedMutex();
        files = new FileService();
        detector = new FailureDetector();
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing configuration attribute: " + key);
        }
        return value.trim();
    }

    public static ServantInfo me() {
        return me;
    }

    public static ServantInfo bootstrap() {
        return bootstrap;
    }

    public static long weakLimit() {
        return weakLimit;
    }

    public static long strongLimit() {
        return strongLimit;
    }

    public static int teamLimit() {
        return teamLimit;
    }

    public static SystemState state() {
        return state;
    }

    public static FileIndex index() {
        return index;
    }

    public static Storage storage() {
        return storage;
    }

    public static WorkDir workDir() {
        return workDir;
    }

    public static SuzukiKasamiDistributedMutex mutex() {
        return mutex;
    }

    public static FileService files() {
        return files;
    }

    public static FailureDetector detector() {
        return detector;
    }

    public static void timestampedStandardPrint(String message) {
        LOGGER.info(message);
    }

    public static void timestampedErrorPrint(String message) {
        LOGGER.severe(message);
    }

    public static void timestampedErrorPrint(String message, Throwable cause) {
        LOGGER.log(Level.SEVERE, message, cause);
    }
}
