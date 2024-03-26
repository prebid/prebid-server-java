package org.prebid.server.log;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.message.FormattedMessage;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.spi.ExtendedLogger;

public class Logger {

    private static final String FQCN = Logger.class.getCanonicalName();

    private final ExtendedLogger delegate;

    Logger(ExtendedLogger delegate) {
        this.delegate = delegate;
    }

    public boolean isWarnEnabled() {
        return delegate.isWarnEnabled();
    }

    public boolean isInfoEnabled() {
        return delegate.isInfoEnabled();
    }

    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }

    public boolean isTraceEnabled() {
        return delegate.isTraceEnabled();
    }

    public void fatal(Object message) {
        log(Level.FATAL, message);
    }

    public void fatal(Object message, Throwable t) {
        log(Level.FATAL, message, t);
    }

    public void error(Object message) {
        log(Level.ERROR, message);
    }

    public void error(Object message, Object... params) {
        log(Level.ERROR, message.toString(), params);
    }

    public void error(Object message, Throwable t) {
        log(Level.ERROR, message, t);
    }

    public void error(Object message, Throwable t, Object... params) {
        log(Level.ERROR, message.toString(), t, params);
    }

    public void warn(Object message) {
        log(Level.WARN, message);
    }

    public void warn(Object message, Object... params) {
        log(Level.WARN, message.toString(), params);
    }

    public void warn(Object message, Throwable t) {
        log(Level.WARN, message, t);
    }

    public void warn(Object message, Throwable t, Object... params) {
        log(Level.WARN, message.toString(), t, params);
    }

    public void info(Object message) {
        log(Level.INFO, message);
    }

    public void info(Object message, Object... params) {
        log(Level.INFO, message.toString(), params);
    }

    public void info(Object message, Throwable t) {
        log(Level.INFO, message, t);
    }

    public void info(Object message, Throwable t, Object... params) {
        log(Level.INFO, message.toString(), t, params);
    }

    public void debug(Object message) {
        log(Level.DEBUG, message);
    }

    public void debug(Object message, Object... params) {
        log(Level.DEBUG, message.toString(), params);
    }

    public void debug(Object message, Throwable t) {
        log(Level.DEBUG, message, t);
    }

    public void debug(Object message, Throwable t, Object... params) {
        log(Level.DEBUG, message.toString(), t, params);
    }

    public void trace(Object message) {
        log(Level.TRACE, message);
    }

    public void trace(Object message, Object... params) {
        log(Level.TRACE, message.toString(), params);
    }

    public void trace(Object message, Throwable t) {
        log(Level.TRACE, message.toString(), t);
    }

    public void trace(Object message, Throwable t, Object... params) {
        log(Level.TRACE, message.toString(), t, params);
    }

    private void log(Level level, Object message) {
        log(level, message, null);
    }

    private void log(Level level, Object message, Throwable t) {
        if (message instanceof Message) {
            delegate.logIfEnabled(FQCN, level, null, (Message) message, t);
        } else {
            delegate.logIfEnabled(FQCN, level, null, message, t);
        }
    }

    private void log(Level level, String message, Object... params) {
        delegate.logIfEnabled(FQCN, level, null, message, params);
    }

    private void log(Level level, String message, Throwable t, Object... params) {
        delegate.logIfEnabled(FQCN, level, null, new FormattedMessage(message, params), t);
    }
}
