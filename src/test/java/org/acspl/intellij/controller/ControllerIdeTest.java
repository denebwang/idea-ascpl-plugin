package org.acspl.intellij.controller;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.util.xmlb.XmlSerializer;
import org.acspl.intellij.controller.debug.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.*;
import org.junit.Assume;

public class ControllerIdeTest extends BasePlatformTestCase {
    public void testSettingsRoundTripAndNoAutomaticConnection() {
        var data = new ControllerSettings.Data();
        var profile = ControllerEngineTest.environment(); profile.connection = "ETHERNET";
        data.environments.add(profile); data.selectedId = profile.id;
        var xml = XmlSerializer.serialize(data);
        var restored = XmlSerializer.deserialize(xml, ControllerSettings.Data.class);
        var settings = ControllerSettings.get(getProject()); settings.loadState(restored);
        assertEquals(profile.id, settings.selected().id);
        assertEquals("demo.prg", settings.selected().mappings.getFirst().file);
        assertEquals(2, settings.selected().mappings.getFirst().buffer);
        assertFalse(ControllerManager.get(getProject()).connected());
        var configurable = new ControllerConfigurable(getProject());
        assertNotNull(configurable.createComponent()); assertFalse(configurable.isModified());
        configurable.reset(); assertFalse(configurable.isModified()); configurable.disposeUIResources();
        settings.loadState(new ControllerSettings.Data());
    }
    public void testRegisteredLineBreakpointsPersistAndExcludeOtherLanguages() {
        var file = myFixture.addFileToProject("demo.prg", "LOCAL INT n\nn=1\nSTOP\n").getVirtualFile();
        var other = myFixture.addFileToProject("plain.txt", "text").getVirtualFile();
        var type = XDebuggerUtil.getInstance().findBreakpointType(AcsplBreakpointType.class);
        assertNotNull(type); assertTrue(type.canPutAt(file, 1, getProject())); assertFalse(type.canPutAt(other, 1, getProject()));
        assertTrue(type.getVisibleStandardPanels().isEmpty());
        var manager = XDebuggerManager.getInstance(getProject()).getBreakpointManager();
        XLineBreakpoint<AcsplBreakpointProperties> breakpoint = manager.addLineBreakpoint(type, file.getUrl(), 1, new AcsplBreakpointProperties());
        assertEquals(1, breakpoint.getLine()); assertEquals(file.getUrl(), breakpoint.getFileUrl());
        manager.removeBreakpoint(breakpoint);
    }
    public void testLegacyEncodingSettingIsIgnoredWithoutLosingEnvironment() {
        var profile = ControllerEngineTest.environment();
        var xml = XmlSerializer.serialize(profile);
        xml.addContent(new org.jdom.Element("option").setAttribute("name", "encoding").setAttribute("value", "GBK"));
        var restored = XmlSerializer.deserialize(xml, ControllerEnvironment.class);
        restored.validate();
        assertEquals(profile.id, restored.id); assertEquals(profile.dllPath, restored.dllPath);
        assertEquals("demo.prg", restored.mappings.getFirst().file);
        assertEquals(2, restored.mappings.getFirst().buffer);
        assertFalse(new com.google.gson.Gson().toJson(restored.copy()).contains("encoding"));
    }
    public void testRealDebuggerSessionWithMockSdk() throws Exception {
        String dll = System.getProperty("acspl.mock.sdk");
        Assume.assumeTrue("Requires Windows mock SDK fixture", dll != null && System.getProperty("os.name").startsWith("Windows"));
        var file = myFixture.addFileToProject("debug.prg", "LOCAL INT counter\ncounter=1\nWAIT 100\nSTOP\n").getVirtualFile();
        var environment = ControllerEngineTest.environment(); environment.dllPath = dll;
        var manager = new ControllerManager(getProject(), WindowsSdkTransport::new,
            Path.of("build", "tmp", "controller-ide-" + java.util.UUID.randomUUID()).toAbsolutePath());
        com.intellij.openapi.util.Disposer.register(getTestRootDisposable(), manager);
        var source = new ControllerEngine.Source(file.getUrl(), 2, "LOCAL INT counter\ncounter=1\nWAIT 100\nSTOP\n");
        AtomicBoolean ready = new AtomicBoolean(); AtomicReference<Exception> error = new AtomicReference<>();
        manager.submit(() -> { manager.connect(environment); return manager.engine().compile(source); },
            result -> ready.set(true), ex -> { error.set(ex); ready.set(true); });
        PlatformTestUtil.waitWithEventsDispatching("Compile mock controller", ready::get, 20000);
        assertNull(error.get());
        var type = XDebuggerUtil.getInstance().findBreakpointType(AcsplBreakpointType.class);
        var breakpoints = XDebuggerManager.getInstance(getProject()).getBreakpointManager();
        var breakpoint = breakpoints.addLineBreakpoint(type, file.getUrl(), 2, new AcsplBreakpointProperties());
        var session = XDebuggerManager.getInstance(getProject()).newSessionBuilder(new XDebugProcessStarter() {
            public XDebugProcess start(XDebugSession session) throws com.intellij.execution.ExecutionException {
                return new AcsplDebugProcess(session, manager, source);
            }
        }).sessionName("ACSPL+ mock native debugger").showTab(true).startSession().getSession();
        manager.setDebugging(true);
        try {
            PlatformTestUtil.waitWithEventsDispatching("Hit line breakpoint", session::isSuspended, 20000);
            assertNotNull(session.getCurrentStackFrame());
            assertEquals(2, session.getCurrentStackFrame().getSourcePosition().getLine());
            assertEquals(file, session.getCurrentStackFrame().getSourcePosition().getFile());
            AtomicReference<String> watch = new AtomicReference<>();
            session.getCurrentStackFrame().getEvaluator().evaluate("counter",
                new com.intellij.xdebugger.evaluation.XDebuggerEvaluator.XEvaluationCallback() {
                    public void evaluated(com.intellij.xdebugger.frame.XValue value) { watch.set("ok"); }
                    public void errorOccurred(String message) { watch.set(message); }
                }, session.getCurrentPosition());
            PlatformTestUtil.waitWithEventsDispatching("Read buffer variable", () -> watch.get() != null, 20000);
            assertEquals("ok", watch.get());
            session.stepOver(false);
            PlatformTestUtil.waitWithEventsDispatching("Step to next controller line", () -> session.isSuspended() &&
                session.getCurrentStackFrame() != null && session.getCurrentStackFrame().getSourcePosition().getLine() == 3, 20000);
            session.stepInto();
            PlatformTestUtil.waitWithEventsDispatching("Unsupported step keeps paused view", session::isSuspended, 20000);
            assertEquals(3, session.getCurrentStackFrame().getSourcePosition().getLine());
            session.stop();
            PlatformTestUtil.waitWithEventsDispatching("Stop mock controller", () -> !manager.debugging() && !manager.busy(), 20000);
        } finally {
            session.stop(); breakpoints.removeBreakpoint(breakpoint);
            // Stopped debugger tabs retain consoles intentionally; the test must close its tab.
            var descriptor = session.getRunContentDescriptor();
            if (descriptor != null) {
                com.intellij.execution.ui.RunContentManager.getInstance(getProject()).removeRunContent(
                    com.intellij.execution.executors.DefaultDebugExecutor.getDebugExecutorInstance(), descriptor);
                com.intellij.openapi.util.Disposer.dispose(descriptor);
            }
            // The headless light fixture has a mock content manager, so dispose its session tab explicitly.
            var tab = ((com.intellij.xdebugger.impl.XDebugSessionImpl) session).getSessionTabIfInitialized();
            if (tab != null) com.intellij.openapi.util.Disposer.dispose(tab);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        }
    }
}
