package org.acspl.intellij.controller;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Buffer operations deliberately have no retries and no implicit compile/run on connection. */
public final class ControllerEngine implements AutoCloseable {
    public record Source(String url, int buffer, String text) {
        public Source { ControllerProtocol.buffer(buffer); text = normalize(text); }
        public int lines() { return (int) text.chars().filter(c -> c == '\n').count() + 1; }
    }
    public record Compilation(Source source, Path backup, String reply) {}
    private final ControllerTransport transport;
    private final ControllerEnvironment environment;
    private final Path backupRoot;
    private final Map<Integer, Source> compiled = new HashMap<>();
    private volatile boolean connected;
    public ControllerEngine(ControllerTransport transport, ControllerEnvironment environment, Path backupRoot) {
        this.transport = transport; this.environment = environment.copy(); this.backupRoot = backupRoot;
    }
    public String connect() throws IOException {
        environment.validate();
        String info = transport.connect(environment);
        connected = true;
        try {
            String controller = query("#SI");
            var version = java.util.regex.Pattern.compile("(?i)Controller\\s*Firmware\\s*Version\\s*:\\s*(\\d+\\.\\d+)").matcher(controller);
            if (version.find() && !version.group(1).equals("3.12"))
                throw new IllegalStateException("目标固件为 " + version.group(1) + "，此集成仅针对 3.12。");
            return info + "\n" + controller;
        }
        catch (IOException | RuntimeException e) { close(); throw e; }
    }
    public boolean connected() { return connected; }
    public ControllerProtocol.Status status(int buffer) throws IOException {
        return ControllerProtocol.status(buffer, query("?" + ControllerProtocol.buffer(buffer)));
    }
    public Compilation compile(Source source) throws IOException {
        requireConnection();
        byte[] bytes = AsciiSource.encode(source.text);
        if (status(source.buffer).active()) throw new IllegalStateException("缓冲区正在运行或暂停，请先停止该缓冲区。");
        // Fail closed: a successful durable backup is required before a destructive load.
        byte[] original = upload(source.buffer);
        Path dir = backupRoot.resolve(environment.id);
        Files.createDirectories(dir);
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now());
        Path backup = Files.createTempFile(dir, "buffer-" + source.buffer + "-" + stamp + "-", ".prg");
        Files.write(backup, original, StandardOpenOption.TRUNCATE_EXISTING);
        compiled.remove(source.buffer);
        try { transport.load(source.buffer, bytes); } catch (IOException ex) { close(); throw ex; }
        String reply = query(ControllerProtocol.command(source.buffer, "C"));
        // Detect transfer/newline/encoding discrepancies before associating editor positions.
        String uploaded = AsciiSource.decode(upload(source.buffer));
        if (!normalize(uploaded).equals(source.text))
            throw new IllegalStateException("控制器源码与编辑器不一致，已拒绝运行/调试。原脚本备份：" + backup);
        compiled.put(source.buffer, source);
        return new Compilation(source, backup, reply);
    }
    public void verify(Source source) throws IOException {
        requireConnection();
        AsciiSource.validate(source.text);
        if (!source.equals(compiled.get(source.buffer))) throw new IllegalStateException("文件或映射已更改，请重新下载编译。");
        String remote = AsciiSource.decode(upload(source.buffer));
        if (!normalize(remote).equals(source.text)) {
            compiled.remove(source.buffer);
            throw new IllegalStateException("控制器缓冲区被外部修改，请重新下载编译。");
        }
        var s = status(source.buffer);
        if (s.active()) throw new IllegalStateException("缓冲区已经运行或暂停。");
        if (s.state() != ControllerProtocol.State.COMPILED && s.state() != ControllerProtocol.State.TERMINATED)
            throw new IllegalStateException("缓冲区尚未编译。");
    }
    public String run(Source source) throws IOException { verify(source); return query(ControllerProtocol.command(source.buffer, "X")); }
    public String saveFlash(Source source) throws IOException {
        verify(source);
        return query(ControllerProtocol.saveProgram(source.buffer));
    }
    public String debug(Source source, Collection<Integer> lines, boolean muted) throws IOException {
        verify(source);
        for (int line : lines) if (line < 1 || line > source.lines()) throw new IllegalArgumentException("断点行号超出已编译源码。");
        query(ControllerProtocol.command(source.buffer, "BR")); // Only this buffer; never ##BR.
        if (!muted) for (int line : new TreeSet<>(lines)) query(ControllerProtocol.breakpoint(source.buffer, line, true));
        return query(ControllerProtocol.command(source.buffer, "XD"));
    }
    public String control(int buffer, String op) throws IOException {
        if (!Set.of("P", "S", "XS", "XD").contains(op)) throw new IllegalArgumentException("不支持的调试操作。");
        if ((op.equals("XS") || op.equals("XD")) && status(buffer).state() != ControllerProtocol.State.SUSPENDED)
            throw new IllegalStateException("单步/继续只适用于已暂停的缓冲区。");
        return query(ControllerProtocol.command(buffer, op));
    }
    public String clearBreakpoints(int buffer) throws IOException { return query(ControllerProtocol.command(buffer, "BR")); }
    public String setBreakpoint(int buffer, int line, boolean add) throws IOException {
        return query(ControllerProtocol.breakpoint(buffer, line, add));
    }
    public String read(int buffer, String expression) throws IOException {
        Source source = compiled.get(buffer);
        boolean local = WatchReference.local(source == null ? "" : source.text(), expression);
        return query(ControllerProtocol.read(buffer, expression, local));
    }
    private String query(String command) throws IOException {
        requireConnection();
        try {
            String response = transport.transaction(command);
            ControllerProtocol.checkReply(response);
            return response.strip();
        } catch (ControllerProtocol.ControllerError e) {
            String detail = "";
            try { detail = transport.transaction("??" + e.code).strip(); } catch (Exception ignored) {}
            throw new ControllerProtocol.ControllerError(e.code, e.line, e.getMessage() + "\n" + detail);
        } catch (IOException e) {
            close(); // Lost communications do not imply the physical controller has stopped.
            throw e;
        }
    }
    private void requireConnection() { if (!connected) throw new IllegalStateException("请先连接控制器。"); }
    private byte[] upload(int buffer) throws IOException {
        try { return transport.upload(buffer); } catch (IOException ex) { close(); throw ex; }
    }
    public static String normalize(String text) { return text.replace("\r\n", "\n").replace('\r', '\n'); }
    @Override public void close() { connected = false; compiled.clear(); transport.close(); }
}
