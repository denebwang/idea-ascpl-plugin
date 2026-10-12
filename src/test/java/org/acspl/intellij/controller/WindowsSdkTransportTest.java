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
    @Test public void ethernet313FirmwareConnectsWithZeroHandleAndIndependentLibraryVersion() throws Exception {
        var profile = environment.copy(); profile.connection = "ETHERNET"; profile.host = "mock-firmware313";
        try (var engine = new ControllerEngine(new WindowsSdkTransport(), profile, temp.getRoot().toPath().resolve("backup"))) {
            var info = engine.connect();
            assertTrue(info, info.startsWith("ACS C Library 7.2.0.0 / ETHERNET"));
            assertTrue(info, info.contains("Controller Firmware Version:3.13.01.00"));
            assertTrue(engine.connected());
            assertEquals(ControllerProtocol.State.NOT_COMPILED, engine.status(2).state());
        }
    }
    @Test public void zeroValuedSimulatorAndEthernetHandlesAreUsableAndClosed() throws Exception {
        Path marker = Path.of(environment.dllPath + ".closed");
        for (String kind : List.of("SIMULATOR", "ETHERNET")) {
            Files.deleteIfExists(marker);
            var profile = environment.copy(); profile.connection = kind; profile.host = "mock-zero";
            try (var transport = new WindowsSdkTransport()) {
                assertTrue(transport.connect(profile).contains(kind));
                assertEquals("0", transport.transaction("?MOCK_HANDLE").strip());
                assertEquals(Integer.toString(profile.timeoutMs), transport.transaction("?MOCK_TIMEOUT").strip());
                byte[] program = AsciiSource.encode("STOP\n");
                transport.load(2, program); assertArrayEquals(program, transport.upload(2));
            }
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while ((!Files.exists(marker) || !Files.readString(marker).equals("0")) && System.nanoTime() < deadline)
                Thread.sleep(25);
            assertTrue("CloseComm must receive the zero-valued " + kind + " handle", Files.exists(marker));
            assertEquals("0", Files.readString(marker));
        }
    }
    @Test public void nonzeroEthernetHandlesRetainTheirFull64BitValue() throws Exception {
        for (String host : List.of("mock-positive", "mock-wide")) {
            var profile = environment.copy(); profile.connection = "ETHERNET"; profile.host = host;
            try (var transport = new WindowsSdkTransport()) {
                transport.connect(profile);
                assertEquals(host.equals("mock-wide") ? "4294967297" : "1", transport.transaction("?MOCK_HANDLE").strip());
            }
        }
    }
    @Test public void invalidEthernetHandleReportsOpeningOperationAndNativeError() throws Exception {
        var profile = environment.copy(); profile.connection = "ETHERNET"; profile.host = "mock-fail";
        try (var transport = new WindowsSdkTransport()) {
            var error = assertThrows(IOException.class, () -> transport.connect(profile));
            assertTrue(error.getMessage(), error.getMessage().contains("acsc_OpenCommEthernetTCP"));
            assertTrue(error.getMessage(), error.getMessage().contains("405"));
            assertFalse(error.getMessage(), error.getMessage().contains("System.Management.Automation"));
            assertThrows(IOException.class, () -> transport.transaction("#SI"));
        }
    }
    @Test public void timeoutFailureAfterValidZeroHandleStillFailsConnection() throws Exception {
        var profile = environment.copy(); profile.timeoutMs = 500;
        try (var transport = new WindowsSdkTransport()) {
            var error = assertThrows(IOException.class, () -> transport.connect(profile));
            assertTrue(error.getMessage(), error.getMessage().contains("acsc_SetTimeout"));
            assertTrue(error.getMessage(), error.getMessage().contains("406"));
            assertThrows(IOException.class, () -> transport.transaction("#SI"));
        }
    }
    @Test public void invalidHandleWithoutSdkErrorCodeIsNotTreatedAsConnected() throws Exception {
        var profile = environment.copy(); profile.connection = "ETHERNET"; profile.host = "mock-fail-no-error";
        try (var transport = new WindowsSdkTransport()) {
            var error = assertThrows(IOException.class, () -> transport.connect(profile));
            assertTrue(error.getMessage(), error.getMessage().contains("acsc_OpenCommEthernetTCP"));
            assertTrue(error.getMessage(), error.getMessage().contains("ACS SDK error 0"));
            assertTrue(error.getMessage(), error.getMessage().contains("no error code"));
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
