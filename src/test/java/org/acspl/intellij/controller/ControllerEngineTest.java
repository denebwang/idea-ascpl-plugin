package org.acspl.intellij.controller;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class ControllerEngineTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private final ControllerEnvironment env = environment();
    private final Fake transport = new Fake();
    private ControllerEngine engine;
    static ControllerEnvironment environment() {
        var e = new ControllerEnvironment(); e.dllPath = "C:/SDK/acsc64.dll";
        e.mappings.add(new ControllerEnvironment.BufferMapping("demo.prg", 2)); return e;
    }
    @Before public void setup() throws Exception { engine = new ControllerEngine(transport, env, temp.getRoot().toPath()); engine.connect(); }
    @After public void cleanup() { engine.close(); }
    ControllerEngine.Source source() { return new ControllerEngine.Source("file://demo.prg", 2, "LOCAL INT i\n! ASCII comment\nSTOP\n"); }
    @Test public void connectAndSettingsAreInert() {
        assertEquals(List.of("connect", "#SI"), transport.actions);
        assertTrue(engine.connected());
        assertEquals(2, env.mappings.getFirst().buffer);
    }
    @Test public void backupPrecedesLoadAndCompileAndBytesRoundTrip() throws Exception {
        byte[] original = "STOP\r\n".getBytes(StandardCharsets.US_ASCII); transport.program = original;
        var result = engine.compile(source());
        assertArrayEquals(original, Files.readAllBytes(result.backup()));
        assertEquals(source().text().replace("\n", "\r\n"), new String(transport.program, StandardCharsets.US_ASCII));
        assertEquals(List.of("connect", "#SI", "?2", "upload:2", "load:2", "#2C", "upload:2"), transport.actions);
        assertEquals("OK", engine.run(source()));
        assertEquals("#2X", transport.actions.getLast());
        assertFalse(transport.actions.contains("##X"));
    }
    @Test public void runningAndUnknownStatesRefuseDownload() throws Exception {
        for (String state : List.of("running in line 3", "suspended in line 3", "unknown")) {
            transport.state = state;
            assertThrows(IllegalStateException.class, () -> engine.compile(source()));
            assertFalse(transport.actions.contains("load:2"));
        }
    }
    @Test public void backupFailureRefusesLoad() throws Exception {
        var file = temp.newFile("not-a-directory").toPath();
        var other = new ControllerEngine(transport, env, file);
        other.connect();
        assertThrows(IOException.class, () -> other.compile(source()));
        assertFalse(transport.actions.contains("load:2"));
    }
    @Test public void compileErrorRetainsBackupAndReportsLine() throws Exception {
        transport.compileReply = "?2026 in line 3\r\n";
        var error = assertThrows(ControllerProtocol.ControllerError.class, () -> engine.compile(source()));
        assertEquals(3, error.line); assertTrue(error.getMessage().contains("Mock compilation error"));
        try (var backups = Files.list(temp.getRoot().toPath().resolve(env.id))) { assertEquals(1, backups.count()); }
        assertThrows(IllegalStateException.class, () -> engine.run(source()));
        assertFalse(transport.actions.contains("#2X"));
    }
    @Test public void editsRemoteChangesAndFailedTransfersInvalidateRun() throws Exception {
        engine.compile(source());
        assertThrows(IllegalStateException.class, () -> engine.run(new ControllerEngine.Source(source().url(), 2, "STOP")));
        assertThrows(IllegalStateException.class, () -> engine.run(new ControllerEngine.Source("other.prg", 2, source().text())));
        transport.program = "STOP".getBytes(StandardCharsets.US_ASCII);
        assertThrows(IllegalStateException.class, () -> engine.run(source()));
        assertFalse(transport.actions.contains("#2X"));
    }
    @Test public void debugOwnBufferWithOneBasedDeduplicatedBreakpoints() throws Exception {
        engine.compile(source()); engine.debug(source(), List.of(3, 1, 3), false);
        assertEquals(List.of("#2BR", "#2BS1", "#2BS3", "#2XD"), transport.actions.subList(transport.actions.size() - 4, transport.actions.size()));
        transport.state = "suspended in line 1";
        engine.control(2, "XS"); assertEquals("#2XS", transport.actions.getLast());
        engine.control(2, "XD"); assertEquals("#2XD", transport.actions.getLast());
        engine.control(2, "P"); engine.control(2, "S");
        transport.state = "terminated in line 4";
        assertThrows(IllegalStateException.class, () -> engine.control(2, "XS"));
        assertThrows(IllegalArgumentException.class, () -> engine.control(2, "SR"));
    }
    @Test public void mutedBreakpointsAreNotInstalledAndBadLinesNeverRun() throws Exception {
        engine.compile(source());
        assertThrows(IllegalArgumentException.class, () -> engine.debug(source(), List.of(999), false));
        assertFalse(transport.actions.contains("#2XD"));
        engine.debug(source(), List.of(1), true);
        assertFalse(transport.actions.contains("#2BS1")); assertEquals("#2XD", transport.actions.getLast());
    }
    @Test public void readOnlyWatchSyntaxRejectsCommandInjectionAndExpressions() throws Exception {
        engine.compile(new ControllerEngine.Source("test.prg", 2, "LOCAL REAL data(2)(4)\nSTOP\n"));
        assertEquals("value", engine.read(2, "data(0)(3)"));
        assertEquals("?2:data(0)(3)", transport.actions.getLast());
        engine.read(2, "MST(0).#MOVE"); assertEquals("?MST(0).#MOVE", transport.actions.getLast());
        for (String s : List.of("x\r#2X", "x\n", "x;#2X", "x=1", "f(x)", "x+1", "?0", "2:x", "x(-1)", "x(\"a\")"))
            assertThrows(s, IllegalArgumentException.class, () -> engine.read(2, s));
    }
    @Test public void watchResolutionDistinguishesArraysFunctionsAndBufferScopes() {
        String source = "GLOBAL INT globalName\nLOCAL REAL array(3), scalar\n" +
            "REAL functionName(INT arg) {\nLOCAL INT privateName\nRET arg\n}\nSTOP\n";
        assertTrue(WatchReference.local(source, "array(1)")); assertTrue(WatchReference.local(source, "scalar"));
        assertFalse(WatchReference.local(source, "globalName")); assertFalse(WatchReference.local(source, "FPOS0"));
        assertFalse(WatchReference.local(source, "MST(0).#MOVE"));
        for (String text : List.of("scalar(1)", "functionName(1)", "unknownFunction(1)", "arg", "privateName", "SQRT(1)"))
            assertThrows(text, IllegalArgumentException.class, () -> WatchReference.local(source, text));
    }
    @Test public void lossClosesConnectionWithoutStopOrRetries() throws Exception {
        transport.fail = true;
        assertThrows(IOException.class, () -> engine.status(2)); assertFalse(engine.connected());
        assertFalse(transport.actions.contains("#2S"));
        assertEquals(1, transport.actions.stream().filter(s -> s.equals("?2")).count());
    }
    @Test public void firmwareMismatchEncodingAndEnvironmentValidation() throws Exception {
        transport.firmware = "4.20";
        assertThrows(IllegalStateException.class, () -> engine.connect()); assertFalse(engine.connected());
        assertThrows(AsciiSource.SourceError.class, () -> AsciiSource.encode("中文"));
        assertThrows(AsciiSource.SourceError.class, () -> AsciiSource.encode("a\0b"));
        env.mappings.add(new ControllerEnvironment.BufferMapping("other.prg", 2));
        assertThrows(IllegalArgumentException.class, env::validate);
        env.mappings.clear(); env.mappings.add(new ControllerEnvironment.BufferMapping("../demo.prg", 2));
        assertThrows(IllegalArgumentException.class, env::validate);
    }
    @Test public void statusParserCoversManualExamplesAndNeverConfusesNotCompiled() {
        assertEquals(ControllerProtocol.State.NOT_COMPILED, ControllerProtocol.status(1, "Buffer1: 18 lines,not compiled").state());
        assertEquals(3, ControllerProtocol.status(0, "Buffer 0: 5 lines, suspended in line 3\r\n").line());
        assertEquals(ControllerProtocol.State.TERMINATED, ControllerProtocol.status(0, "Buffer 0: 5 lines,terminated in line5").state());
        assertThrows(IllegalStateException.class, () -> ControllerProtocol.status(1, "Buffer 0: 5 lines, compiled"));
    }
    @Test public void nonAsciiRejectedBeforeStatusBackupLoadAndCompile() throws Exception {
        byte[] original = "STOP\r\n".getBytes(StandardCharsets.US_ASCII);
        transport.program = original.clone();
        for (String text : List.of("! 中文\nSTOP\n", "DISP \"中文\"\nSTOP\n", "! caf\u00e9\n", "! \ud83d\ude00\n",
            "\ufeffSTOP\n", "PTP\u30000,100\n", "! \u0080\n")) {
            var source = new ControllerEngine.Source("bad.prg", 2, text);
            assertThrows(AsciiSource.SourceError.class, () -> engine.compile(source));
            assertThrows(AsciiSource.SourceError.class, () -> engine.run(source));
            assertThrows(AsciiSource.SourceError.class, () -> engine.debug(source, List.of(), false));
            assertThrows(AsciiSource.SourceError.class, () -> engine.saveFlash(source));
        }
        assertEquals(List.of("connect", "#SI"), transport.actions);
        assertArrayEquals(original, transport.program);
        try (var files = Files.list(temp.getRoot().toPath())) { assertEquals(0, files.count()); }
    }
    @Test public void asciiCoordinatesAndNewlinesArePreservedWithoutReplacement() throws Exception {
        var error = assertThrows(AsciiSource.SourceError.class, () -> AsciiSource.encode("STOP\r\n! 中\n"));
        assertEquals(2, error.line); assertEquals(3, error.column); assertEquals(0x4e2d, error.codePoint);
        assertTrue(error.getMessage().contains("U+4E2D"));
        var emoji = assertThrows(AsciiSource.SourceError.class, () -> AsciiSource.encode("! \ud83d\ude00"));
        assertEquals(0x1f600, emoji.codePoint);
        String text = "! ASCII\tcomment\nDISP \"quotes, !$()\"\r\nSTOP\r";
        assertArrayEquals("! ASCII\tcomment\r\nDISP \"quotes, !$()\"\r\nSTOP\r\n".getBytes(StandardCharsets.US_ASCII), AsciiSource.encode(text));
        assertThrows(IOException.class, () -> AsciiSource.decode(new byte[]{(byte)0x80}));
    }
    @Test public void nonAsciiRemoteProgramRefusesRunAndDebug() throws Exception {
        engine.compile(source()); transport.actions.clear();
        transport.program = "STOP\n! 中文".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> engine.run(source()));
        assertThrows(IOException.class, () -> engine.debug(source(), List.of(1), false));
        assertThrows(IOException.class, () -> engine.saveFlash(source()));
        assertEquals(List.of("upload:2", "upload:2", "upload:2"), transport.actions);
    }
    @Test public void flashSavePersistsOnlyTheVerifiedBufferWithoutRunningOrDownloading() throws Exception {
        byte[] other = "! Other saved buffer\r\nSTOP\r\n".getBytes(StandardCharsets.US_ASCII);
        transport.flash.put(0, other.clone()); transport.flash.put(63, other.clone());
        engine.compile(source());
        assertFalse(transport.actions.stream().anyMatch(s -> s.startsWith("#SAVE")));
        for (String state : List.of("compiled", "terminated in line 4")) {
            transport.state = state; transport.actions.clear();
            assertEquals("OK", engine.saveFlash(source()));
            assertEquals(List.of("upload:2", "?2", "#SAVEPROG 2"), transport.actions);
            assertArrayEquals(AsciiSource.encode(source().text()), transport.flash.get(2));
            assertArrayEquals(other, transport.flash.get(0)); assertArrayEquals(other, transport.flash.get(63));
            assertEquals(3, transport.flash.size());
        }
    }
    @Test public void flashSaveRequiresCompilationAndUnchangedFileAndMapping() throws Exception {
        assertThrows(IllegalStateException.class, () -> engine.saveFlash(source()));
        assertEquals(List.of("connect", "#SI"), transport.actions);
        engine.compile(source()); transport.actions.clear();
        for (var changed : List.of(new ControllerEngine.Source(source().url(), 2, "STOP\n"),
            new ControllerEngine.Source("other.prg", 2, source().text()),
            new ControllerEngine.Source(source().url(), 3, source().text())))
            assertThrows(IllegalStateException.class, () -> engine.saveFlash(changed));
        assertTrue(transport.actions.isEmpty()); assertTrue(transport.flash.isEmpty());
        transport.compileReply = "?2026 in line 3";
        assertThrows(ControllerProtocol.ControllerError.class, () -> engine.compile(source()));
        transport.actions.clear();
        assertThrows(IllegalStateException.class, () -> engine.saveFlash(source()));
        assertTrue(transport.actions.isEmpty()); assertTrue(transport.flash.isEmpty());
    }
    @Test public void flashSaveRejectsActiveUncompiledAndUnknownStates() throws Exception {
        engine.compile(source()); transport.actions.clear();
        for (String state : List.of("running in line 3", "suspended in line 3", "not compiled", "edit", "empty", "unknown")) {
            transport.state = state;
            assertThrows(IllegalStateException.class, () -> engine.saveFlash(source()));
        }
        assertFalse(transport.actions.stream().anyMatch(s -> s.startsWith("#SAVE")));
        assertTrue(transport.flash.isEmpty());
    }
    @Test public void flashSaveRemoteChangesInvalidateCompilationEvenAfterRestoringBytes() throws Exception {
        engine.compile(source()); transport.actions.clear();
        byte[] original = transport.program.clone(); transport.program = AsciiSource.encode("STOP\n");
        assertThrows(IllegalStateException.class, () -> engine.saveFlash(source()));
        assertEquals(List.of("upload:2"), transport.actions);
        transport.program = original; transport.actions.clear();
        assertThrows(IllegalStateException.class, () -> engine.saveFlash(source()));
        assertTrue(transport.actions.isEmpty()); assertTrue(transport.flash.isEmpty());
    }
    @Test public void flashSaveControllerErrorsAreReportedWithoutFallbackOrRetry() throws Exception {
        engine.compile(source()); transport.actions.clear(); transport.saveReply = "?5030";
        var error = assertThrows(ControllerProtocol.ControllerError.class, () -> engine.saveFlash(source()));
        assertEquals(5030, error.code); assertTrue(error.getMessage().contains("Mock protection violation"));
        assertEquals(List.of("upload:2", "?2", "#SAVEPROG 2", "??5030"), transport.actions);
        assertTrue(engine.connected()); assertTrue(transport.flash.isEmpty());
    }
    @Test public void flashSaveLostAcknowledgementClosesWithoutRetryStopOrRestart() throws Exception {
        engine.compile(source()); transport.actions.clear(); transport.failSave = true;
        assertThrows(IOException.class, () -> engine.saveFlash(source()));
        assertFalse(engine.connected());
        assertEquals(List.of("upload:2", "?2", "#SAVEPROG 2", "close"), transport.actions);
        // A lost reply does not prove the write failed. Retrying would cause another flash write.
        assertArrayEquals(transport.program, transport.flash.get(2));
        assertThrows(IllegalStateException.class, () -> engine.saveFlash(source()));
        assertEquals(1, transport.actions.stream().filter("#SAVEPROG 2"::equals).count());
    }
    @Test public void flashCommandAlwaysHasOneValidNumericBuffer() {
        assertEquals("#SAVEPROG 0", ControllerProtocol.saveProgram(0));
        assertEquals("#SAVEPROG 63", ControllerProtocol.saveProgram(63));
        for (int buffer : List.of(-1, 64, Integer.MAX_VALUE))
            assertThrows(IllegalArgumentException.class, () -> ControllerProtocol.saveProgram(buffer));
    }
    static final class Fake implements ControllerTransport {
        final List<String> actions = new ArrayList<>();
        final Map<Integer, byte[]> flash = new HashMap<>();
        byte[] program = new byte[0];
        String state = "not compiled", compileReply = "4 lines compiled", firmware = "3.12", saveReply = "OK";
        boolean fail, failSave;
        public String connect(ControllerEnvironment e) { actions.add("connect"); return "Mock SDK"; }
        public byte[] upload(int buffer) { actions.add("upload:" + buffer); return program.clone(); }
        public void load(int buffer, byte[] bytes) { actions.add("load:" + buffer); program = bytes.clone(); }
        public String transaction(String command) throws IOException {
            actions.add(command);
            if (fail) throw new IOException("lost");
            if (command.equals("#SI")) return "Controller Firmware Version:" + firmware;
            if (command.equals("?2")) return "Buffer 2: 4 lines," + state;
            if (command.equals("#2C")) { state = "compiled"; return compileReply; }
            if (command.equals("??2026")) return "Mock compilation error";
            if (command.equals("??5030")) return "Mock protection violation";
            if (command.equals("#SAVEPROG 2")) {
                if (!saveReply.startsWith("?")) flash.put(2, program.clone());
                if (failSave) throw new IOException("Lost flash acknowledgement");
                return saveReply;
            }
            return command.startsWith("?") ? "value" : "OK";
        }
        public void close() { actions.add("close"); }
    }
}
