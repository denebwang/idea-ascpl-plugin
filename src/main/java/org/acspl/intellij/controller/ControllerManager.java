package org.acspl.intellij.controller;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

/** All native requests, including debugger polling, use the same serial executor. */
public final class ControllerManager implements Disposable {
    @FunctionalInterface public interface Task<T> { T run() throws Exception; }
    private final Project project;
    private final Supplier<ControllerTransport> transportFactory;
    private final Path backupRoot;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ACS controller commands"); t.setDaemon(true); return t;
    });
    private final CopyOnWriteArrayList<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicInteger pending = new AtomicInteger();
    private volatile ControllerEngine engine;
    private volatile ControllerEnvironment activeEnvironment;
    private volatile boolean disposed;
    private volatile boolean debugging;
    public ControllerManager(Project project) { this(project, WindowsSdkTransport::new, null); }
    ControllerManager(Project project, Supplier<ControllerTransport> transportFactory, Path backupRoot) {
        this.project = project; this.transportFactory = transportFactory; this.backupRoot = backupRoot;
    }
    public static ControllerManager get(Project project) { return project.getService(ControllerManager.class); }
    public boolean connected() { return engine != null && engine.connected(); }
    public boolean busy() { return pending.get() > 0; }
    public boolean debugging() { return debugging; }
    public void setDebugging(boolean value) { debugging = value; }
    public String activeId() { return activeEnvironment == null ? "" : activeEnvironment.id; }
    public ControllerEngine engine() {
        if (!connected()) throw new IllegalStateException("控制器未连接。");
        return engine;
    }
    public void connect(ControllerEnvironment environment) throws Exception {
        if (connected()) throw new IllegalStateException("请先断开当前连接。");
        if (backupRoot == null && project.getBasePath() == null) throw new IllegalStateException("请先打开本地项目目录。");
        // Use only generated UUIDs as backup subdirectories, never arbitrary paths from project XML.
        java.util.UUID.fromString(environment.id);
        var next = new ControllerEngine(transportFactory.get(), environment,
            backupRoot != null ? backupRoot : Path.of(project.getBasePath(), ".acspl", "backups"));
        try {
            String info = next.connect();
            engine = next; activeEnvironment = environment.copy(); log(info);
        } catch (Exception ex) { next.close(); throw ex; }
    }
    public void disconnect() { if (engine != null) engine.close(); engine = null; activeEnvironment = null; log("连接已断开。实体控制器的程序可能仍在运行。"); }
    public void ensureEnvironment(ControllerEnvironment selected) {
        if (activeEnvironment == null || !new com.google.gson.Gson().toJson(activeEnvironment).equals(new com.google.gson.Gson().toJson(selected)))
            throw new IllegalStateException("环境设置已更改，请断开后重新连接。");
    }
    public <T> void submit(Task<T> task, Consumer<T> success, Consumer<Exception> failure) {
        if (disposed) return;
        pending.incrementAndGet();
        try { executor.execute(() -> {
            T value = null; Exception error = null;
            try { value = task.run(); } catch (Exception ex) { error = ex; log(ex.getMessage()); }
            finally { pending.decrementAndGet(); }
            T result = value; Exception problem = error;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!disposed && !project.isDisposed()) { if (problem == null) success.accept(result); else failure.accept(problem); }
            });
        }); } catch (RejectedExecutionException ex) { pending.decrementAndGet(); if (!disposed) throw ex; }
    }
    public void addListener(Consumer<String> listener, Disposable parent) {
        listeners.add(listener);
        com.intellij.openapi.util.Disposer.register(parent, () -> listeners.remove(listener));
    }
    public void log(String message) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!disposed && !project.isDisposed()) for (var listener : listeners) listener.accept(message);
        });
    }
    public void dispose() {
        disposed = true; executor.shutdownNow(); listeners.clear();
        ControllerEngine current = engine;
        // Native request timeout/cleanup must never block project disposal on the UI thread.
        if (current != null) Thread.ofPlatform().daemon().name("ACS SDK cleanup").start(current::close);
    }
}
