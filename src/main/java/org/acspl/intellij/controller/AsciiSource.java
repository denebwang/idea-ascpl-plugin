package org.acspl.intellij.controller;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;

/** Controller program text is ASCII, including comments and string literals. */
public final class AsciiSource {
    private AsciiSource() {}
    public static void validate(String text) {
        int line = 1, column = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c > 0x7f || c == 0)
                throw new SourceError(line, column, text.codePointAt(i));
            if (c == '\r') {
                line++; column = 1;
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
            } else if (c == '\n') { line++; column = 1; }
            else column++;
        }
    }
    public static byte[] encode(String text) {
        validate(text);
        return ControllerEngine.normalize(text).replace("\n", "\r\n").getBytes(StandardCharsets.US_ASCII);
    }
    public static void validateBytes(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++)
            if (bytes[i] < 0 || bytes[i] == 0)
                throw new IllegalArgumentException("ACSPL+ 源码必须为 ASCII，且不能包含 NUL；字节偏移：" + i);
    }
    public static String decode(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.US_ASCII.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            throw new IOException("控制器上传的源码包含非 ASCII 字节，已拒绝运行/调试。", ex);
        }
    }
    public static final class SourceError extends IllegalArgumentException {
        public final int line, column, codePoint;
        SourceError(int line, int column, int codePoint) {
            super(codePoint == 0 ? "ACSPL+ 源码不能包含 NUL：第 " + line + " 行，第 " + column + " 列。" :
                "ACSPL+ 源码必须为 ASCII：第 " + line + " 行，第 " + column + " 列包含 " +
                    String.format(java.util.Locale.ROOT, "U+%04X", codePoint) + "。注释和字符串也必须为 ASCII。");
            this.line = line; this.column = column; this.codePoint = codePoint;
        }
    }
}
