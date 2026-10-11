package org.acspl.intellij.controller;

import java.nio.file.Path;
import java.util.*;

/** User-owned SDK binaries are loaded only after an explicit connection request. */
public final class ControllerEnvironment {
    public String id = UUID.randomUUID().toString();
    public String name = "ACS 3.12 Simulator";
    public String dllPath = "";
    public String connection = "SIMULATOR";
    public String host = "192.168.0.100";
    public int port = 701;
    public int timeoutMs = 3000;
    public List<BufferMapping> mappings = new ArrayList<>();

    public static final class BufferMapping {
        public String file = "";
        public int buffer;
        public BufferMapping() {}
        public BufferMapping(String file, int buffer) { this.file = file; this.buffer = buffer; }
    }
    public ControllerEnvironment copy() {
        var e = new ControllerEnvironment();
        e.id = id; e.name = name; e.dllPath = dllPath; e.connection = connection;
        e.host = host; e.port = port; e.timeoutMs = timeoutMs;
        for (var m : mappings) e.mappings.add(new BufferMapping(m.file, m.buffer));
        return e;
    }
    public void validate() {
        UUID.fromString(id);
        if (name.isBlank() || dllPath.isBlank()) throw new IllegalArgumentException("请输入环境名称和 SDK 原生 DLL 路径。");
        Path.of(dllPath);
        if (!Set.of("SIMULATOR", "ETHERNET").contains(connection)) throw new IllegalArgumentException("未知连接方式。");
        if (connection.equals("ETHERNET") && !host.matches("[A-Za-z0-9_.:-]+"))
            throw new IllegalArgumentException("控制器地址不能为空或包含空格。");
        if (port < 1 || port > 65535 || timeoutMs < 500 || timeoutMs > 30000)
            throw new IllegalArgumentException("端口需为 1–65535，超时需为 500–30000 ms。");
        Set<String> files = new HashSet<>(); Set<Integer> buffers = new HashSet<>();
        for (var m : mappings) {
            if (m.file.isBlank() || Path.of(m.file).isAbsolute() || m.file.contains("\\") ||
                Arrays.asList(m.file.split("/")).contains(".."))
                throw new IllegalArgumentException("映射请使用项目相对路径，以 / 分隔，不能包含 ..。");
            ControllerProtocol.buffer(m.buffer);
            if (!files.add(m.file) || !buffers.add(m.buffer))
                throw new IllegalArgumentException("同一环境内，文件和缓冲区不能重复映射。");
        }
    }
}
