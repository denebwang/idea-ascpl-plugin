package org.acspl.intellij.controller;

import java.util.regex.*;

/** ACSPL+ 3.12 Commands/Variables Reference §6.2, §6.3.5–6.3.6 (pp. 812, 829–836). */
public final class ControllerProtocol {
    public enum State { RUNNING, SUSPENDED, TERMINATED, COMPILED, NOT_COMPILED, EDIT, EMPTY }
    public record Status(State state, int line, String raw) {
        public boolean active() { return state == State.RUNNING || state == State.SUSPENDED; }
    }
    private static final Pattern STATUS = Pattern.compile(
        "(?i)Buffer\\s*(\\d+)\\s*:.*?\\b(not\\s*compiled|running|run|suspended|terminated|compiled|edit(?:ing)?|empty)\\b(?:.*?\\bline\\s*(\\d+))?",
        Pattern.DOTALL);
    private static final Pattern ERROR = Pattern.compile("\\?\\s*(\\d+)(?:\\s+in\\s+line\\s*(\\d+))?", Pattern.CASE_INSENSITIVE);
    // Only a name, numeric array indices, and optional bit selector. No evaluated function calls,
    // statement separators, buffer prefixes or arbitrary terminal commands.
    private static final Pattern READ = Pattern.compile(
        "[A-Za-z_][A-Za-z_0-9]*(?:\\(\\d+\\)){0,2}(?:\\.(?:\\d+|#[A-Za-z_][A-Za-z_0-9]*))?");
    private ControllerProtocol() {}
    public static int buffer(int n) {
        if (n < 0 || n > 63) throw new IllegalArgumentException("缓冲区编号需为 0–63；此版本不下载 D-buffer。");
        return n;
    }
    public static String command(int buffer, String operation) { return "#" + buffer(buffer) + operation; }
    // The buffer argument is mandatory here: omitting it would save every buffer.
    public static String saveProgram(int buffer) { return "#SAVEPROG " + buffer(buffer); }
    public static String breakpoint(int buffer, int line, boolean add) {
        if (line < 1) throw new IllegalArgumentException("控制器行号从 1 开始。");
        return command(buffer, (add ? "BS" : "BR") + line);
    }
    public static String read(int buffer, String expression, boolean local) {
        buffer(buffer);
        if (!READ.matcher(expression).matches() || expression.length() > 256)
            throw new IllegalArgumentException("只支持变量、数字数组下标和位访问，例如 FPOS(0)、samples(3)、MST(0).#MOVE。");
        return "?" + (local ? buffer + ":" : "") + expression;
    }
    public static Status status(int expectedBuffer, String response) {
        Matcher m = STATUS.matcher(response);
        if (!m.find() || Integer.parseInt(m.group(1)) != expectedBuffer)
            throw new IllegalStateException("无法识别缓冲区状态，已拒绝后续操作：" + response.strip());
        String word = m.group(2).toUpperCase();
        State state = word.startsWith("NOT") ? State.NOT_COMPILED : word.startsWith("EDIT") ? State.EDIT :
            word.equals("RUN") ? State.RUNNING : State.valueOf(word);
        return new Status(state, m.group(3) == null ? -1 : Integer.parseInt(m.group(3)), response.strip());
    }
    public static void checkReply(String response) {
        Matcher m = ERROR.matcher(response);
        if (m.find()) throw new ControllerError(Integer.parseInt(m.group(1)),
            m.group(2) == null ? -1 : Integer.parseInt(m.group(2)), response.strip());
    }
    public static final class ControllerError extends IllegalStateException {
        public final int code, line;
        ControllerError(int code, int line, String response) { super(response); this.code = code; this.line = line; }
    }
}
