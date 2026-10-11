package org.acspl.intellij.controller;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.*;
import com.intellij.ui.content.ContentFactory;
import org.acspl.intellij.AcsplFileType;
import org.acspl.intellij.controller.debug.AcsplDebugProcess;
import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class ControllerToolWindowFactory implements ToolWindowFactory {
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        var view = new View(project);
        var content = ContentFactory.getInstance().createContent(view.panel, "", false);
        content.setDisposer(view); toolWindow.getContentManager().addContent(content);
    }
    private static final class View implements Disposable {
        final Project project;
        final ControllerManager manager;
        final JPanel panel = new JPanel(new BorderLayout(0, 8));
        final JTextArea log = new JTextArea();
        final JComboBox<String> environments = new JComboBox<>();
        List<ControllerEnvironment> profiles = List.of();
        View(Project project) {
            this.project = project; manager = ControllerManager.get(project);
            var controls = new JPanel(new GridLayout(0, 4, 6, 6));
            controls.add(environments);
            button(controls, "环境设置", this::settings);
            button(controls, "连接", () -> {
                var environment = selected();
                execute(() -> { manager.connect(environment); return "已连接。"; });
            });
            button(controls, "断开", () -> {
                if (manager.debugging()) throw new IllegalStateException("请先在 Debug 窗口停止本次调试，再断开连接。");
                execute(() -> { manager.disconnect(); return ""; });
            });
            button(controls, "下载并编译当前文件", this::compile);
            button(controls, "保存到 Flash", this::saveFlash);
            button(controls, "运行当前文件", () -> start(false));
            button(controls, "调试当前文件", () -> start(true));
            button(controls, "缓冲区状态", () -> {
                var source = source();
                execute(() -> manager.engine().status(source.buffer()).raw());
            });
            button(controls, "停止当前缓冲区", () -> {
                var source = source(); execute(() -> manager.engine().control(source.buffer(), "S"));
            });
            panel.add(controls, BorderLayout.NORTH);
            log.setEditable(false); log.setLineWrap(true); log.setWrapStyleWord(true);
            panel.add(new JScrollPane(log), BorderLayout.CENTER);
            panel.add(new JLabel("操作当前编辑器中的映射文件。下载会备份并替换缓冲区；保存到 Flash 需单独点击。"), BorderLayout.SOUTH);
            manager.addListener(this::append, this); reload();
            append("先在环境设置中配置 ACS SDK 3.12 x64 DLL、连接方式和文件 → 缓冲区映射。");
        }
        void button(JPanel p, String text, Runnable task) {
            var b = new JButton(text); b.addActionListener(e -> {
                try { task.run(); } catch (Exception ex) { error(ex); }
            }); p.add(b);
        }
        void settings() {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, ControllerConfigurable.class);
            reload();
        }
        void reload() {
            profiles = ControllerSettings.get(project).getState().environments.stream().map(ControllerEnvironment::copy).toList();
            environments.removeAllItems();
            int index = 0;
            for (int i = 0; i < profiles.size(); i++) {
                environments.addItem(profiles.get(i).name);
                if (profiles.get(i).id.equals(ControllerSettings.get(project).getState().selectedId)) index = i;
            }
            if (!profiles.isEmpty()) environments.setSelectedIndex(index);
        }
        ControllerEnvironment selected() {
            int i = environments.getSelectedIndex();
            if (i < 0) throw new IllegalStateException("请先配置环境。");
            var e = profiles.get(i).copy();
            ControllerSettings.get(project).getState().selectedId = e.id;
            return e;
        }
        ControllerEngine.Source source() {
            var env = selected(); manager.ensureEnvironment(env);
            VirtualFile[] files = FileEditorManager.getInstance(project).getSelectedFiles();
            if (files.length == 0 || files[0].getFileType() != AcsplFileType.INSTANCE)
                throw new IllegalStateException("请在编辑器打开 ACSPL+ 文件。");
            var file = files[0];
            var base = project.getBasePath() == null ? null : LocalFileSystem.getInstance().findFileByPath(project.getBasePath());
            if (base == null) throw new IllegalStateException("无法读取项目目录。");
            String relative = VfsUtilCore.getRelativePath(file, base, '/');
            var mapping = env.mappings.stream().filter(m -> m.file.equals(relative)).findFirst()
                .orElseThrow(() -> new IllegalStateException("当前文件尚未配置缓冲区映射：" + relative));
            var document = FileDocumentManager.getInstance().getDocument(file);
            if (document == null) throw new IllegalStateException("无法读取编辑器文档。");
            return new ControllerEngine.Source(file.getUrl(), mapping.buffer, document.getText());
        }
        void compile() {
            if (manager.debugging()) throw new IllegalStateException("请先停止本次调试。");
            var source = source();
            AsciiSource.validate(source.text());
            if (!confirm("下载并编译", source, "将备份并替换此缓冲区。编译会初始化变量、激活 ON 自动例程；已有自动例程可能执行。")) return;
            if (manager.busy()) throw new IllegalStateException("控制器正在处理请求，请稍后操作。");
            manager.submit(() -> {
                var result = manager.engine().compile(source);
                return result.reply() + "\n原脚本备份：" + result.backup();
            }, this::append, ex -> {
                error(ex);
                var target = VirtualFileManager.getInstance().findFileByUrl(source.url());
                var document = target == null ? null : FileDocumentManager.getInstance().getDocument(target);
                if (ex instanceof ControllerProtocol.ControllerError e && e.line > 0 &&
                    document != null && ControllerEngine.normalize(document.getText()).equals(source.text()) && e.line <= source.lines())
                    new OpenFileDescriptor(project, target, e.line - 1, 0).navigate(true);
            });
        }
        void start(boolean debug) {
            if (manager.debugging()) throw new IllegalStateException("已有 ACSPL+ 调试会话。");
            var source = source();
            AsciiSource.validate(source.text());
            if (!confirm(debug ? "调试" : "运行", source, "将执行已编译的脚本，可能触发运动、I/O 或自动例程。")) return;
            if (debug) {
                if (manager.busy()) throw new IllegalStateException("控制器正在处理请求，请稍后操作。");
                manager.submit(() -> { manager.engine().verify(source); return source; }, s -> {
                    try { AcsplDebugProcess.start(project, manager, s); } catch (Exception ex) { error(ex); }
                }, this::error);
            } else execute(() -> manager.engine().run(source));
        }
        void saveFlash() {
            if (manager.debugging()) throw new IllegalStateException("请先在 Debug 窗口停止本次调试，再保存到 Flash。");
            var source = source();
            AsciiSource.validate(source.text());
            if (manager.busy()) throw new IllegalStateException("控制器正在处理请求，请稍后操作。");
            if (!confirm("保存到 Flash", source,
                "将已下载并编译的当前缓冲区程序写入 Flash，覆盖该缓冲区已有的 Flash 程序。" +
                "请先停止此缓冲区。下次上电会加载此程序；包含 AUTOEXEC 时可能自动执行。")) return;
            execute(() -> {
                String reply = manager.engine().saveFlash(source);
                return "控制器已确认将缓冲区 " + source.buffer() + " 的程序保存到 Flash。" + (reply.isBlank() ? "" : "\n" + reply);
            });
        }
        boolean confirm(String title, ControllerEngine.Source source, String detail) {
            return Messages.showYesNoDialog(project, "环境：" + selected().name + "\n目标：" + selected().connection +
                (selected().connection.equals("ETHERNET") ? " " + selected().host + ":" + selected().port : "") +
                "\n缓冲区：" + source.buffer() + "\n文件：" + source.url() + "\n\n" + detail,
                "ACSPL+ " + title, "执行", "取消", Messages.getWarningIcon()) == Messages.YES;
        }
        void execute(ControllerManager.Task<String> task) {
            if (manager.busy()) throw new IllegalStateException("控制器正在处理请求，请稍后操作。");
            manager.submit(task, this::append, this::error);
        }
        void append(String message) {
            if (message == null || message.isBlank()) return;
            log.append(message + "\n"); log.setCaretPosition(log.getDocument().getLength());
        }
        void error(Exception ex) {
            append(ex.getMessage());
            Messages.showErrorDialog(project, ex.getMessage() == null ? ex.toString() : ex.getMessage(), "ACSPL+ Controller");
            if (ex instanceof AsciiSource.SourceError e) {
                var files = FileEditorManager.getInstance(project).getSelectedFiles();
                if (files.length > 0) new OpenFileDescriptor(project, files[0], e.line - 1, e.column - 1).navigate(true);
            }
        }
        public void dispose() {}
    }
}
