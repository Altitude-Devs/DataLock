package com.alttd.datalock;

public class Logger {

    static private final org.slf4j.Logger logger;

    static {
        logger = DataLock.getLogger();
    }

    public static void info(String info, String... variables)
    {
        for (String variable : variables) {
            info = info.replaceFirst("%", variable);
        }
        logger.info(info);
    }

    public static void warn(String warning, String... variables)
    {
        for (String variable : variables) {
            warning = warning.replaceFirst("%", variable);
        }
        logger.warn(warning);
    }

    public static void error(String severe, String... variables)
    {
        for (String variable : variables) {
            severe = severe.replaceFirst("%", variable);
        }
        logger.error(severe);
    }
}
