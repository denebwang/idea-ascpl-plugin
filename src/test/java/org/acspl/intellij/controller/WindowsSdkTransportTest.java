package org.acspl.intellij.controller;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import static org.junit.Assert.*;

/** Optional Windows integration test against the test-only exported C SDK fixture. */
public class WindowsSdkTransportTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    ControllerEnvironment environment;
    @Before public void setup() throws Exception {
        Assume.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        String dll = System.getProperty("acspl.mock.sdk");
        Assume.assumeTrue("Run with -PmockSdkDll=<compiled tools/mock_sdk.c>", dll != null);
        Path copy = temp.getRoot().toPath().resolve("mock sdk 中文.dll");
        Files.copy(Path.of(dll), copy);
        environment = ControllerEngineTest.environment(); environment.dllPath = copy.toString();
    }
    @Test public void sdkMarshallingJsonUnicodeAndChunkedUpload() throws Exception {
        try (var transport = new WindowsSdkTransport()) {
            assertTrue(transport.connect(environment).contains("SIMULATOR"));
            assertTrue(transport.transaction("#SI").contains("3.12"));
            assertEquals("42\r\n", transport.transaction("?FPOS(0)"));
            byte[] bytes = ("! ASCII with \"quotes\" and $() and \\ paths\r\n").repeat(1600).getBytes(StandardCharsets.US_ASCII);
            assertTrue(bytes.length > 16384);
            transport.load(2, bytes); assertArrayEquals(bytes, transport.upload(2));
            assertThrows(IllegalArgumentException.class, () -> transport.load(2, "! 中文".getBytes(StandardCharsets.UTF_8)));
            assertThrows(IllegalArgumentException.class, () -> transport.load(2, new byte[]{0}));
            assertArrayEquals(bytes, transport.upload(2));
        }
    }
    @Test public void cLibraryFileVersionIsReportedSeparatelyFromControllerFirmware() throws Exception {
        try (var engine = new ControllerEngine(new WindowsSdkTransport(), environment, temp.getRoot().toPath().resolve("backup"))) {
            var info = engine.connect();
            assertTrue(info, info.startsWith("ACS C Library 7.2.0.0 / SIMULATOR"));
            assertTrue(info, info.contains("Controller Firmware Version:3.12.01.00"));
            assertTrue(engine.connected());
        }
    }
    @Test public void fullCompileRunPauseStepResumeAndStopThroughNativeBridge() throws Exception {
        try (var engine = new ControllerEngine(new WindowsSdkTransport(), environment, temp.getRoot().toPath().resolve("backup"))) {
            engine.connect();
            var source = new ControllerEngine.Source("test.prg", 2, "LOCAL INT counter\ncounter=1\nSTOP\n");
            engine.compile(source); engine.run(source); engine.control(2, "P"); engine.control(2, "S");
            engine.debug(source, List.of(3), false);
            assertEquals(3, engine.status(2).line());
            engine.control(2, "XS"); assertEquals(4, engine.status(2).line());
            engine.control(2, "XD"); assertEquals(3, engine.status(2).line());
            assertEquals("42", engine.read(2, "counter"));
            engine.control(2, "S");
        }
    }
    @Test public void nativeErrorTerminatesWorkerAndDoesNotRetry() throws Exception {
        try (var transport = new WindowsSdkTransport()) {
            transport.connect(environment);
            var error = assertThrows(IOException.class, () -> transport.transaction("?FAIL"));
            assertTrue(error.getMessage().contains("402"));
            assertThrows(IOException.class, () -> transport.transaction("?FPOS(0)"));
        }
    }
    @Test public void flashSaveUsesAddressedCommandAndPreservesStoredBytesAcrossRamEdits() throws Exception {
        var transport = new WindowsSdkTransport();
        try (var engine = new ControllerEngine(transport, environment, temp.getRoot().toPath().resolve("backup"))) {
            engine.connect();
            var source = new ControllerEngine.Source("test.prg", 2, "LOCAL INT counter\ncounter=1\nSTOP\n");
            assertEquals("0", transport.transaction("?MOCK_FLASH_SAVE_COUNT").strip());
            engine.compile(source);
            assertEquals("0", transport.transaction("?MOCK_FLASH_SAVE_COUNT").strip());
            assertEquals("", engine.saveFlash(source));
            assertEquals("1", transport.transaction("?MOCK_FLASH_SAVE_COUNT").strip());
            assertEquals("2", transport.transaction("?MOCK_FLASH_BUFFER").strip());
            assertEquals("1", transport.transaction("?MOCK_FLASH_MATCH").strip());
            assertEquals(Integer.toString(AsciiSource.encode(source.text()).length),
                transport.transaction("?MOCK_FLASH_LENGTH").strip());
            byte[] changed = AsciiSource.encode("STOP\n"); transport.load(2, changed);
            assertEquals("0", transport.transaction("?MOCK_FLASH_MATCH").strip());
            assertThrows(IllegalStateException.class, () -> engine.saveFlash(source));
            assertEquals("1", transport.transaction("?MOCK_FLASH_SAVE_COUNT").strip());
            assertArrayEquals(changed, transport.upload(2));
        }
    }
}
