package org.acspl.intellij.controller;

import java.io.IOException;

/** Serial, synchronous SDK operations, invoked off the IDE UI thread. */
public interface ControllerTransport extends AutoCloseable {
    String connect(ControllerEnvironment environment) throws IOException;
    byte[] upload(int buffer) throws IOException;
    void load(int buffer, byte[] program) throws IOException;
    String transaction(String command) throws IOException;
    @Override void close();
}
