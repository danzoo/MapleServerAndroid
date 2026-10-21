package config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import net.server.Server;
import org.slf4j.LoggerFactory;

import java.io.File;

// Desktop fork of the Android LogBackConfig: logback-android's LogcatAppender does not exist on a
// plain JVM, so logcat output is replaced by a ConsoleAppender (stdout). The rolling file appender
// under Context.getFilesDir() is kept unchanged.
public class LogBackConfig {
    public static void configure() {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = lc.getLogger(Logger.ROOT_LOGGER_NAME);

        File logFile = new File(Server.getInstance().getContext().getFilesDir(), "server.log");
        if (logFile.exists()) {
            logFile.delete();
        }

        FileAppender<ILoggingEvent> fileAppender = setFileLogger(lc, logFile);
        ConsoleAppender<ILoggingEvent> consoleAppender = setConsoleLogger(lc);

        rootLogger.addAppender(fileAppender);
        rootLogger.addAppender(consoleAppender);
        rootLogger.setLevel(Level.INFO);
    }

    private static FileAppender<ILoggingEvent> setFileLogger(LoggerContext lc, File logFile) {
        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setFile(logFile.getAbsolutePath());
        fileAppender.setContext(lc);

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        encoder.setContext(lc);
        encoder.start();

        fileAppender.setEncoder(encoder);
        fileAppender.start();

        return fileAppender;
    }

    private static ConsoleAppender<ILoggingEvent> setConsoleLogger(LoggerContext lc) {
        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setContext(lc);

        PatternLayoutEncoder consoleEncoder = new PatternLayoutEncoder();
        consoleEncoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        consoleEncoder.setContext(lc);
        consoleEncoder.start();

        consoleAppender.setEncoder(consoleEncoder);
        consoleAppender.start();

        return consoleAppender;
    }
}
