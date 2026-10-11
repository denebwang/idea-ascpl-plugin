package org.acspl.intellij.controller;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** SDK isolation: JSON over stdin/stdout; no user source/commands are interpolated into a shell. */
public final class WindowsSdkTransport implements ControllerTransport {
    private static final Gson JSON = new Gson();
    private final BlockingQueue<String> replies = new LinkedBlockingQueue<>();
    private Process process;
    private BufferedWriter input;
    private Path script;
    private int deadlineMs;
    private long nextId;

    public synchronized String connect(ControllerEnvironment e) throws IOException {
        if (!System.getProperty("os.name").startsWith("Windows")) throw new IOException("控制器功能需要 Windows。");
        if (!Files.isRegularFile(Path.of(e.dllPath))) throw new IOException("SDK DLL 不存在：" + e.dllPath);
        close();
        script = Files.createTempFile("acspl-sdk-worker-", ".ps1");
        try (var resource = getClass().getResourceAsStream("/controller/windows-sdk-worker.ps1")) {
            if (resource == null) throw new IOException("SDK 桥接资源缺失。");
            Files.copy(resource, script, StandardCopyOption.REPLACE_EXISTING);
        }
        String systemRoot = System.getenv("SystemRoot");
        Path powershell = Path.of(systemRoot == null ? "C:\\Windows" : systemRoot,
            "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
        deadlineMs = Math.max(15000, e.timeoutMs * 3 + 5000);
        try {
            process = new ProcessBuilder(powershell.toString(), "-NoLogo", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-File", script.toString()).redirectErrorStream(true).start();
            input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            replies.clear();
            Process worker = process;
            Thread.ofPlatform().daemon().name("ACS SDK replies").start(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(worker.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null)
                        if (line.startsWith("ACSPL_JSON:")) replies.offer(line.substring(11));
                } catch (IOException ignored) {}
                replies.offer("{\"ok\":false,\"error\":\"SDK 桥接进程已退出。\"}");
            });
            return request("connect", Map.of("dll", e.dllPath, "connection", e.connection, "host", e.host,
                "port", e.port, "timeout", e.timeoutMs)).get("result").getAsString();
        } catch (IOException | RuntimeException ex) { close(); throw ex; }
    }
    public byte[] upload(int buffer) throws IOException {
        return Base64.getDecoder().decode(request("upload", Map.of("buffer", buffer)).get("result").getAsString());
    }
    public void load(int buffer, byte[] program) throws IOException {
        AsciiSource.validateBytes(program);
        request("load", Map.of("buffer", buffer, "program", Base64.getEncoder().encodeToString(program)));
    }
    public String transaction(String command) throws IOException {
        return request("transaction", Map.of("command", command)).get("result").getAsString();
    }
    private synchronized JsonObject request(String op, Map<String, ?> data) throws IOException {
        if (process == null || !process.isAlive()) throw new IOException("SDK 桥接进程未运行。");
        long id = ++nextId;
        var body = new LinkedHashMap<String, Object>(data); body.put("id", id); body.put("op", op);
        try {
            input.write(JSON.toJson(body)); input.newLine(); input.flush();
            String line = replies.poll(deadlineMs, TimeUnit.MILLISECONDS);
            if (line == null) throw new IOException("SDK 响应超时；连接已断开，控制器的程序可能仍在运行。");
            var reply = JsonParser.parseString(line).getAsJsonObject();
            if (!reply.get("ok").getAsBoolean()) throw new IOException(reply.get("error").getAsString());
            if (reply.get("id").getAsLong() != id) throw new IOException("SDK 响应序号错误。");
            return reply;
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); close(); throw new IOException("SDK 请求已取消。", ex); }
        catch (RuntimeException | IOException ex) { close(); if (ex instanceof IOException io) throw io; throw new IOException(ex); }
    }
    public synchronized void close() {
        if (process != null) {
            // EOF asks the worker to CloseComm. No terminal stop command is issued on disconnect.
            try { process.getOutputStream().close(); } catch (IOException ignored) {}
            Process old = process;
            Thread.ofPlatform().daemon().start(() -> {
                try { if (!old.waitFor(2, TimeUnit.SECONDS)) old.destroyForcibly(); }
                catch (InterruptedException ex) { old.destroyForcibly(); Thread.currentThread().interrupt(); }
            });
            process = null;
        }
        input = null;
        if (script != null) { try { Files.deleteIfExists(script); } catch (IOException ignored) {} script = null; }
    }
}
