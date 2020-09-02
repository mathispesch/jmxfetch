package org.datadog.jmxfetch.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
public class CustomLogger {
    private static final ConcurrentHashMap<String, AtomicInteger> messageCount
            = new ConcurrentHashMap<String, AtomicInteger>();
    private static final String LAYOUT = "%d{yyyy-MM-dd HH:mm:ss z} | JMX | %-5p | %c{1} | %m%n";

    private static final String LAYOUT_RFC3339 =
        "%d{yyyy-MM-dd'T'HH:mm:ss'Z'} | JMX | %-5p | %c{1} | %m%n";

    // log4j2 uses SYSTEM_OUT and SYSTEM_ERR - support both
    private static final String SYSTEM_OUT_ALT = "STDOUT";
    private static final String SYSTEM_ERR_ALT = "STDERR";

    /** Sets up the custom logger to the specified level and location. */
    public static void setup(Level level, String logLocation, boolean logFormatRfc3339) {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        String target = "CONSOLE";

        String logPattern = logFormatRfc3339 ? LAYOUT_RFC3339 : LAYOUT;

        PatternLayout layout = PatternLayout.newBuilder()
            .withConfiguration(config)
            .withPattern(logPattern)
            .build();

        if (logLocation != null
                && !ConsoleAppender.Target.SYSTEM_ERR.toString().equals(logLocation)
                && !SYSTEM_ERR_ALT.equals(logLocation)
                && !ConsoleAppender.Target.SYSTEM_OUT.toString().equals(logLocation)
                && !SYSTEM_OUT_ALT.equals(logLocation)) {

            target = "FileLogger";

            RollingFileAppender fa = RollingFileAppender.newBuilder()
                .setConfiguration(config)
                .withName(target)
                .withLayout(layout)
                .withFileName(logLocation)
                .withFilePattern(logLocation + ".%d")
                .withPolicy(SizeBasedTriggeringPolicy.createPolicy("5MB"))
                .withStrategy(DefaultRolloverStrategy.newBuilder().withMax("1").build())
                .build();

            fa.start();
            config.addAppender(fa);
            ctx.getRootLogger().addAppender(config.getAppender(fa.getName()));

            log.info("File Handler set");
        } else {

            if (logLocation != null
                    && (ConsoleAppender.Target.SYSTEM_ERR.toString().equals(logLocation)
                        || SYSTEM_ERR_ALT.equals(logLocation))) {

                ConsoleAppender console = (ConsoleAppender)config.getAppender("CONSOLE");
                console.stop();
                config.getRootLogger().removeAppender("CONSOLE");
                ctx.updateLoggers();

                ConsoleAppender ca = ConsoleAppender.newBuilder()
                    .setConfiguration(config)
                    .withName(logLocation)
                    .setTarget(ConsoleAppender.Target.SYSTEM_ERR)
                    .withLayout(layout)
                    .build();

                ca.start();
                config.addAppender(ca);
                ctx.getRootLogger().addAppender(config.getAppender(ca.getName()));
            }
        }

        // replace default appender with the correct layout
        LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
        loggerConfig.removeAppender(target);

        Appender appender = ConsoleAppender.newBuilder()
                    .setConfiguration(config)
                    .withName(target)
                    .withLayout(layout)
                    .build();
        appender.start();

        loggerConfig.addAppender(appender, null, null);
        loggerConfig.setLevel(level);

        ctx.updateLoggers();
    }

    /** Laconic logging for reduced verbosity. */
    public static void laconic(org.slf4j.Logger logger, Level level, String message, int max) {
        int messageCount = getAndIncrementMessageCount(message);
        if (messageCount <= max) {
            if (level.isInRange(Level.ERROR, Level.ALL)) {
                logger.error(message);
            } else if (level == Level.WARN) {
                logger.warn(message);
            } else if (level == Level.INFO) {
                logger.info(message);
            } else if (level == Level.DEBUG) {
                logger.debug(message);
            }
        }
    }

    private static int getAndIncrementMessageCount(String message) {
        AtomicInteger count = messageCount.get(message);
        if (null == count) {
            count = new AtomicInteger();
            AtomicInteger winner = messageCount.putIfAbsent(message, count);
            if (winner != null) {
                count = winner;
            }
        }
        return count.getAndIncrement();
    }

    private CustomLogger() {}
}
