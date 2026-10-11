package org.acspl.intellij.controller.debug;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.*;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.evaluation.*;
import com.intellij.xdebugger.frame.*;
import org.acspl.intellij.AcsplFileType;
import org.acspl.intellij.controller.*;
import org.acspl.intellij.symbols.DeclarationModel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One controller buffer is one debugger frame. Firmware does not expose a function call stack here. */
public final class AcsplDebugProcess extends XDebugProcess {
    private final Project project;
    private final ControllerManager manager;
    private final ControllerEngine.Source source;
    private final VirtualFile file;
    private final Map<Integer, XLineBreakpoint<AcsplBreakpointProperties>> breakpoints = new TreeMap<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "ACS debugger status"); t.setDaemon(true); return t;
    });
    private final AtomicBoolean polling = new AtomicBoolean();
    private volatile boolean closed, started, ended;
    private final com.intellij.openapi.Disposable lifetime = () -> { closed = true; timer.shutdownNow(); };
    private boolean paused, obsoleteReported;
    private final XDebuggerEditorsProvider editors = new XDebuggerEditorsProvider() {
        public com.intellij.openapi.fileTypes.FileType getFileType() { return AcsplFileType.INSTANCE; }
        public Document createDocument(Project project, XExpression expression, XSourcePosition position, EvaluationMode mode) {
            return EditorFactory.getInstance().createDocument(expression.getExpression());
        }
        public Document createDocument(Project project, XExpression expression, XSourcePosition position, EvaluationMode mode, String placeholder) {
            return EditorFactory.getInstance().createDocument(expression.getExpression());
        }
    };
    private final XDebuggerEvaluator evaluator = new XDebuggerEvaluator() {
        public void evaluate(String expression, XEvaluationCallback callback, XSourcePosition position) {
            String name = expression.strip();
            try { WatchReference.local(source.text(), name); }
            catch (IllegalArgumentException ex) { callback.errorOccurred(ex.getMessage()); return; }
            manager.submit(() -> manager.engine().read(source.buffer(), name),
                value -> callback.evaluated(new TextValue(value)), ex -> callback.errorOccurred(ex.getMessage()));
        }
        public boolean isCodeFragmentEvaluationSupported() { return false; }
    };
    private final XBreakpointHandler<XLineBreakpoint<AcsplBreakpointProperties>> handler =
        new XBreakpointHandler<>(AcsplBreakpointType.class) {
            public void registerBreakpoint(XLineBreakpoint<AcsplBreakpointProperties> b) {
                if (!source.url().equals(b.getFileUrl())) {
                    getSession().setBreakpointInvalid(b, "Only the active mapped buffer is debugged."); return;
                }
                if (b.getSuspendPolicy() == SuspendPolicy.NONE || b.isLogMessage() || b.isLogStack() ||
                    b.getConditionExpression() != null && !b.getConditionExpression().getExpression().isBlank() ||
                    b.getLogExpressionObject() != null && !b.getLogExpressionObject().getExpression().isBlank()) {
                    getSession().setBreakpointInvalid(b, "ACSPL+ supports unconditional suspending line breakpoints only."); return;
                }
                int line = b.getLine() + 1;
                if (line > source.lines() || !matches()) {
                    getSession().setBreakpointInvalid(b, "Source differs from the compiled buffer."); return;
                }
                breakpoints.put(line, b);
                if (started && !getSession().areBreakpointsMuted()) updateBreakpoint(b, line, true);
            }
            public void unregisterBreakpoint(XLineBreakpoint<AcsplBreakpointProperties> b, boolean temporary) {
                if (!source.url().equals(b.getFileUrl())) return;
                int line = b.getLine() + 1; breakpoints.remove(line);
                if (started && !closed) updateBreakpoint(b, line, false);
            }
        };
    public AcsplDebugProcess(XDebugSession session, ControllerManager manager, ControllerEngine.Source source) throws ExecutionException {
        super(session); this.project = session.getProject(); this.manager = manager; this.source = source;
        file = VirtualFileManager.getInstance().findFileByUrl(source.url());
        if (file == null) throw new ExecutionException("Source file no longer exists.");
        com.intellij.openapi.util.Disposer.register(manager, lifetime);
        session.setPauseActionSupported(true);
        session.addSessionListener(new XDebugSessionListener() {
            public void breakpointsMuted(boolean muted) {
                if (started && !closed) syncBreakpoints();
            }
            public void sessionStopped() { stop(); }
        });
    }
    public static void start(Project project, ControllerManager manager, ControllerEngine.Source source) throws ExecutionException {
        if (manager.debugging()) throw new ExecutionException("An ACSPL+ debug session is already active.");
        manager.setDebugging(true);
        try {
            XDebuggerManager.getInstance(project).newSessionBuilder(new XDebugProcessStarter() {
                public XDebugProcess start(XDebugSession session) throws ExecutionException { return new AcsplDebugProcess(session, manager, source); }
            }).sessionName("ACSPL+ buffer " + source.buffer()).showTab(true).startSession();
        } catch (ExecutionException | RuntimeException ex) { manager.setDebugging(false); throw ex; }
    }
    public XDebuggerEditorsProvider getEditorsProvider() { return editors; }
    public XDebuggerEvaluator getEvaluator() { return evaluator; }
    public XBreakpointHandler<?>[] getBreakpointHandlers() { return new XBreakpointHandler<?>[]{handler}; }
    public boolean checkCanPerformCommands() { return started && !closed; }
    public void sessionInitialized() {
        if (!matches()) { fail(new IllegalStateException("Source changed; compile again before debugging.")); return; }
        var lines = List.copyOf(breakpoints.keySet()); boolean muted = getSession().areBreakpointsMuted();
        manager.submit(() -> manager.engine().debug(source, lines, muted), reply -> {
            if (closed) return;
            started = true; manager.log(reply);
            if (!muted) breakpoints.values().forEach(getSession()::setBreakpointVerified);
            timer.scheduleWithFixedDelay(this::poll, 0, 300, TimeUnit.MILLISECONDS);
        }, this::fail);
    }
    private void poll() {
        if (closed || !polling.compareAndSet(false, true)) return;
        manager.submit(() -> manager.engine().status(source.buffer()), status -> {
            polling.set(false); if (closed) return;
            if (status.state() == ControllerProtocol.State.SUSPENDED && !paused) {
                paused = true;
                int line = status.line();
                boolean accurate = matches() && line >= 1 && line <= source.lines();
                if (!accurate && !obsoleteReported) {
                    obsoleteReported = true;
                    getSession().reportError("Source changed or controller line unavailable. Stop and recompile to restore source positions.");
                }
                var context = context(accurate ? line - 1 : -1);
                var breakpoint = breakpoints.get(line);
                if (accurate && breakpoint != null && !getSession().areBreakpointsMuted())
                    getSession().breakpointReached(breakpoint, null, context);
                else getSession().positionReached(context);
            } else if (status.state() == ControllerProtocol.State.RUNNING && paused) {
                paused = false; getSession().sessionResumed();
            } else if (!status.active()) {
                ended = true; manager.log(status.raw()); getSession().stop();
            }
        }, ex -> { polling.set(false); fail(ex); });
    }
    private XSuspendContext context(int line) {
        XStackFrame frame = new XStackFrame() {
            public XSourcePosition getSourcePosition() { return line < 0 ? null : XDebuggerUtil.getInstance().createPosition(file, line); }
            public XDebuggerEvaluator getEvaluator() { return evaluator; }
            public void computeChildren(XCompositeNode node) {
                var children = new XValueChildrenList();
                DeclarationModel.scan(source.text()).declarations().stream()
                    .filter(d -> d.kind().equals("variable") && d.visibleStart() == 0 && !d.array())
                    .limit(50).forEach(d -> children.add(d.name(), new VariableValue(d)));
                node.addChildren(children, true);
            }
        };
        XExecutionStack stack = new XExecutionStack("Buffer " + source.buffer() + " (no function call stack)") {
            public XStackFrame getTopFrame() { return frame; }
            public void computeStackFrames(int first, XStackFrameContainer container) { container.addStackFrames(first == 0 ? List.of(frame) : List.of(), true); }
        };
        return new XSuspendContext() { public XExecutionStack getActiveExecutionStack() { return stack; } };
    }
    private final class VariableValue extends XValue {
        private final DeclarationModel.Declaration declaration;
        VariableValue(DeclarationModel.Declaration declaration) { this.declaration = declaration; }
        public void computePresentation(XValueNode node, XValuePlace place) {
            manager.submit(() -> manager.engine().read(source.buffer(), declaration.name()),
                value -> node.setPresentation(null, declaration.type(), value, false),
                ex -> node.setPresentation(null, declaration.type(), "<" + ex.getMessage() + ">", false));
        }
    }
    private static final class TextValue extends XValue {
        final String value;
        TextValue(String value) { this.value = value; }
        public void computePresentation(XValueNode node, XValuePlace place) { node.setPresentation(null, null, value, false); }
    }
    private boolean matches() {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        return document != null && ControllerEngine.normalize(document.getText()).equals(source.text());
    }
    private void updateBreakpoint(XLineBreakpoint<AcsplBreakpointProperties> b, int line, boolean add) {
        if (!matches()) { getSession().setBreakpointInvalid(b, "Source changed; stop and recompile."); return; }
        manager.submit(() -> manager.engine().setBreakpoint(source.buffer(), line, add),
            reply -> { if (add && !closed) getSession().setBreakpointVerified(b); }, ex -> getSession().setBreakpointInvalid(b, ex.getMessage()));
    }
    private void syncBreakpoints() {
        if (!matches()) { getSession().reportError("Source changed; stop and recompile."); return; }
        var lines = getSession().areBreakpointsMuted() ? List.<Integer>of() : List.copyOf(breakpoints.keySet());
        manager.submit(() -> {
            manager.engine().clearBreakpoints(source.buffer());
            for (int line : lines) manager.engine().setBreakpoint(source.buffer(), line, true);
            return "";
        }, reply -> {}, this::fail);
    }
    public void resume(XSuspendContext context) { move("XD"); }
    public void startStepOver(XSuspendContext context) { move("XS"); }
    public void startStepInto(XSuspendContext context) { rejectMove(context, "ACSPL+ supports controller-line Step Over only."); }
    public void startStepOut(XSuspendContext context) { rejectMove(context, "Function call stack and Step Out are not available."); }
    public void runToPosition(XSourcePosition position, XSuspendContext context) { rejectMove(context, "Run to Cursor is not available."); }
    private void rejectMove(XSuspendContext context, String message) {
        getSession().reportError(message);
        // The IDE marks a step as resumed before calling the process. Restore the paused view.
        paused = false;
        if (context != null) { paused = true; getSession().positionReached(context); }
    }
    public void startPausing() { manager.submit(() -> manager.engine().control(source.buffer(), "P"), manager::log, this::fail); }
    private void move(String operation) {
        if (!matches()) { getSession().reportError("Source changed; stop and compile again."); return; }
        manager.submit(() -> manager.engine().control(source.buffer(), operation), reply -> {
            paused = false; getSession().sessionResumed(); manager.log(reply);
        }, this::fail);
    }
    private void fail(Exception ex) {
        getSession().reportError(ex.getMessage());
        manager.log("调试连接/操作失败；控制器状态不能据此判断，请通过控制器工具确认。");
        ended = true; getSession().stop();
    }
    public synchronized void stop() {
        if (closed) return;
        closed = true; timer.shutdownNow();
        com.intellij.openapi.util.Disposer.dispose(lifetime);
        var lines = List.copyOf(breakpoints.keySet());
        manager.submit(() -> {
            try {
                if (manager.connected()) {
                    if (!ended) manager.engine().control(source.buffer(), "S");
                    // Remove only breakpoints owned by this session, never other buffers.
                    for (int line : lines) manager.engine().setBreakpoint(source.buffer(), line, false);
                }
            } finally { manager.setDebugging(false); }
            return "";
        }, reply -> {}, ex -> { manager.setDebugging(false); manager.log(ex.getMessage()); });
    }
}
