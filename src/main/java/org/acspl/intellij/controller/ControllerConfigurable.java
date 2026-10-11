package org.acspl.intellij.controller;

import com.google.gson.Gson;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Project settings are inert data. Modification polling never changes the editor. */
public final class ControllerConfigurable implements Configurable {
    private final Project project;
    private JPanel panel;
    private DefaultListModel<EnvironmentDraft> environmentRows;
    private DefaultListModel<MappingDraft> mappingRows;
    private JList<EnvironmentDraft> environments;
    private JList<MappingDraft> mappings;
    private JTextField name, sdk, host, port, timeout, file, buffer;
    private JComboBox<String> connection;
    private JButton removeEnvironment, addMapping, removeMapping, browseSdk, browseFile;
    private EnvironmentDraft selectedEnvironment;
    private MappingDraft selectedMapping;
    private boolean updating;
    private String baseline;
    private static final Gson JSON = new Gson();

    public ControllerConfigurable(Project project) { this.project = project; }
    public String getDisplayName() { return "ACSPL+ Controller"; }
    public JComponent createComponent() {
        environmentRows = new DefaultListModel<>(); mappingRows = new DefaultListModel<>();
        environments = new JList<>(environmentRows); environments.setName("controller.environments");
        mappings = new JList<>(mappingRows); mappings.setName("controller.mappings");
        environments.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        mappings.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        name = text("name"); sdk = text("sdk"); host = text("host"); port = text("port"); timeout = text("timeout");
        file = text("file"); buffer = text("buffer");
        connection = new JComboBox<>(new String[]{"SIMULATOR", "ETHERNET"}); connection.setName("controller.connection");
        browseSdk = button("browseSdk", "浏览…", this::browseSdk);
        browseFile = button("browseFile", "选择文件…", this::browseFile);
        removeEnvironment = button("removeEnvironment", "删除环境", this::removeEnvironment);
        addMapping = button("addMapping", "添加映射", this::addMapping);
        removeMapping = button("removeMapping", "删除映射", this::removeMapping);

        var environmentForm = new JPanel(new GridBagLayout());
        environmentForm.setBorder(BorderFactory.createTitledBorder("环境详情"));
        row(environmentForm, 0, "环境名称", name);
        row(environmentForm, 1, "x64 C 库 DLL", browseRow(sdk, browseSdk));
        row(environmentForm, 2, "连接方式", connection);
        row(environmentForm, 3, "控制器地址（Ethernet）", host);
        row(environmentForm, 4, "TCP 端口（Ethernet）", port);
        row(environmentForm, 5, "超时（500–30000 ms）", timeout);
        port.setToolTipText("1–65535，默认 701；仅 Ethernet 连接使用。");

        var mappingForm = new JPanel(new GridBagLayout());
        row(mappingForm, 0, "项目相对文件路径", browseRow(file, browseFile));
        row(mappingForm, 1, "缓冲区编号（0–63）", buffer);
        row(mappingForm, 2, "", new JLabel("<html>路径用 / 分隔。<br>例如 examples/controller-demo.prg。</html>"));
        var mappingEditor = new JPanel(new BorderLayout()); mappingEditor.add(mappingForm, BorderLayout.NORTH);
        var mappingList = listPanel("当前环境的文件映射", mappings, addMapping, removeMapping);
        mappingList.setMinimumSize(new Dimension(160, 100));
        var mappingSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mappingList, mappingEditor);
        mappingSplit.setResizeWeight(0.3); mappingSplit.setDividerLocation(210);
        mappingSplit.setBorder(BorderFactory.createTitledBorder("文件 → 缓冲区（每个环境中一对一）"));

        var details = new DetailsPanel();
        details.add(environmentForm, BorderLayout.NORTH); details.add(mappingSplit, BorderLayout.CENTER);
        var environmentList = listPanel("环境", environments,
            button("addEnvironment", "添加环境", this::addEnvironment), removeEnvironment);
        environmentList.setPreferredSize(new Dimension(190, 400));
        environmentList.setMinimumSize(new Dimension(160, 100));
        var mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, environmentList, new JScrollPane(details));
        mainSplit.setResizeWeight(0.2); mainSplit.setDividerLocation(190);
        panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("<html>选择左侧环境，在右侧直接填写；文件映射属于所选环境。<br>" +
            "需要 Windows ACS SDK/ADK 3.12 提供的 x64 C 库 DLL（ACSCL_x64.dll）。SIMULATOR 无需填写控制器地址。<br>" +
            "设置不会自动连接或运行。脚本源码固定为 ASCII，包括注释与字符串。</html>"), BorderLayout.NORTH);
        panel.add(mainSplit, BorderLayout.CENTER); panel.setPreferredSize(new Dimension(1000, 520));

        environments.addListSelectionListener(e -> {
            if (!updating && !e.getValueIsAdjusting()) showEnvironment(environments.getSelectedValue());
        });
        mappings.addListSelectionListener(e -> {
            if (!updating && !e.getValueIsAdjusting()) showMapping(mappings.getSelectedValue());
        });
        watch(this::storeEnvironment, name, sdk, host, port, timeout);
        connection.addActionListener(e -> storeEnvironment());
        watch(this::storeMapping, file, buffer);
        reset(); return panel;
    }
    private static JTextField text(String id) {
        var field = new JTextField(24); field.setName("controller." + id); return field;
    }
    private static JButton button(String id, String label, Runnable action) {
        var button = new JButton(label); button.setName("controller." + id);
        button.addActionListener(e -> action.run()); return button;
    }
    private static JPanel browseRow(JTextField field, JButton button) {
        var p = new JPanel(new BorderLayout(6, 0)); p.add(field, BorderLayout.CENTER); p.add(button, BorderLayout.EAST); return p;
    }
    private static JPanel listPanel(String label, JList<?> list, JButton... buttons) {
        var p = new JPanel(new BorderLayout(0, 4)); p.add(new JLabel(label), BorderLayout.NORTH);
        p.add(new JScrollPane(list), BorderLayout.CENTER);
        var controls = new JPanel(new GridLayout(0, 1, 0, 4));
        for (var button : buttons) controls.add(button);
        p.add(controls, BorderLayout.SOUTH); return p;
    }
    private static void row(JPanel p, int row, String label, JComponent field) {
        var c = new GridBagConstraints(); c.gridy = row; c.insets = new Insets(5, 6, 5, 6); c.anchor = GridBagConstraints.WEST;
        var caption = new JLabel(label); caption.setLabelFor(field);
        p.add(caption, c); c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; p.add(field, c);
    }
    private static void watch(Runnable changed, JTextField... fields) {
        var listener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { changed.run(); }
            public void removeUpdate(DocumentEvent e) { changed.run(); }
            public void changedUpdate(DocumentEvent e) { changed.run(); }
        };
        for (var field : fields) field.getDocument().addDocumentListener(listener);
    }
    private void storeEnvironment() {
        if (updating || selectedEnvironment == null) return;
        var e = selectedEnvironment;
        e.name = name.getText(); e.sdk = sdk.getText(); e.host = host.getText();
        e.connection = Objects.toString(connection.getSelectedItem(), "");
        e.port = port.getText(); e.timeout = timeout.getText();
        environments.repaint();
    }
    private void storeMapping() {
        if (updating || selectedMapping == null) return;
        selectedMapping.file = file.getText(); selectedMapping.buffer = buffer.getText(); mappings.repaint();
    }
    private void showEnvironment(EnvironmentDraft e) {
        boolean previous = updating; updating = true;
        try {
            selectedEnvironment = e;
            name.setText(e == null ? "" : e.name); sdk.setText(e == null ? "" : e.sdk);
            connection.setSelectedItem(e == null ? "SIMULATOR" : e.connection);
            host.setText(e == null ? "" : e.host); port.setText(e == null ? "" : e.port); timeout.setText(e == null ? "" : e.timeout);
            for (var component : List.of(name, sdk, connection, host, port, timeout, browseSdk, removeEnvironment, addMapping))
                component.setEnabled(e != null);
            mappingRows.clear();
            if (e != null) for (var m : e.mappings) mappingRows.addElement(m);
            if (!mappingRows.isEmpty()) mappings.setSelectedIndex(0);
            showMapping(mappings.getSelectedValue());
        } finally { updating = previous; }
    }
    private void showMapping(MappingDraft m) {
        boolean previous = updating; updating = true;
        try {
            selectedMapping = m; file.setText(m == null ? "" : m.file); buffer.setText(m == null ? "" : m.buffer);
            for (var component : List.of(file, buffer, browseFile, removeMapping)) component.setEnabled(m != null);
        } finally { updating = previous; }
    }
    private void addEnvironment() {
        var draft = new EnvironmentDraft(new ControllerEnvironment()); int suffix = 1;
        var names = new HashSet<String>();
        for (var e : Collections.list(environmentRows.elements())) names.add(e.name.strip());
        while (names.contains(draft.name + " " + suffix)) suffix++;
        draft.name += " " + suffix; environmentRows.addElement(draft); environments.setSelectedValue(draft, true);
        name.requestFocusInWindow(); name.selectAll();
    }
    private void removeEnvironment() {
        int index = environments.getSelectedIndex(); if (index < 0) return;
        environments.clearSelection(); environmentRows.remove(index);
        if (!environmentRows.isEmpty()) environments.setSelectedIndex(Math.min(index, environmentRows.size() - 1));
    }
    private void addMapping() {
        if (selectedEnvironment == null) return;
        var used = new HashSet<Integer>();
        for (var m : selectedEnvironment.mappings) {
            try { used.add(Integer.parseInt(m.buffer.strip())); } catch (NumberFormatException ignored) {}
        }
        int n = 0; while (used.contains(n) && n < 64) n++;
        if (n == 64) { Messages.showErrorDialog(project, "当前环境的 0–63 缓冲区均已映射。", "ACSPL+ Controller"); return; }
        var m = new MappingDraft(new ControllerEnvironment.BufferMapping("", n));
        selectedEnvironment.mappings.add(m); mappingRows.addElement(m); mappings.setSelectedValue(m, true);
        file.requestFocusInWindow();
    }
    private void removeMapping() {
        int index = mappings.getSelectedIndex(); if (selectedEnvironment == null || index < 0) return;
        mappings.clearSelection(); selectedEnvironment.mappings.remove(index); mappingRows.remove(index);
        if (!mappingRows.isEmpty()) mappings.setSelectedIndex(Math.min(index, mappingRows.size() - 1));
    }
    private void browseSdk() {
        var target = selectedEnvironment; if (target == null) return;
        var descriptor = FileChooserDescriptorFactory.singleFile()
            .withTitle("选择 ACS x64 C 库 DLL（ACSCL_x64.dll）").withExtensionFilter("C Library DLL", "dll");
        var initial = LocalFileSystem.getInstance().findFileByPath(sdk.getText().replace('\\', '/'));
        FileChooser.chooseFile(descriptor, project, panel, initial, chosen -> {
            if (panel != null && selectedEnvironment == target) sdk.setText(chosen.getPath());
        });
    }
    private void browseFile() {
        var target = selectedMapping; if (target == null) return;
        var base = project.getBasePath() == null ? null : LocalFileSystem.getInstance().findFileByPath(project.getBasePath());
        if (base == null) { Messages.showErrorDialog(project, "请先打开本地项目目录。", "ACSPL+ Controller"); return; }
        var descriptor = FileChooserDescriptorFactory.singleFile().withTitle("选择项目中的 ACSPL+ 文件")
            .withExtensionFilter("ACSPL+", "prg", "acspl").withRoots(base).withShowFileSystemRoots(false);
        FileChooser.chooseFile(descriptor, project, panel, base, chosen -> {
            if (panel == null || selectedMapping != target) return;
            String relative = VfsUtilCore.getRelativePath(chosen, base, '/');
            if (relative == null) Messages.showErrorDialog(project, "请选择项目目录中的脚本文件。", "ACSPL+ Controller");
            else file.setText(relative);
        });
    }
    private String draftState() { return JSON.toJson(Collections.list(environmentRows.elements())); }
    public boolean isModified() { return panel != null && !Objects.equals(baseline, draftState()); }
    public void reset() {
        if (panel == null) return;
        boolean previous = updating; updating = true;
        try {
            environmentRows.clear();
            var data = ControllerSettings.get(project).getState(); int selected = 0;
            for (var e : data.environments) {
                if (e.id.equals(data.selectedId)) selected = environmentRows.size();
                environmentRows.addElement(new EnvironmentDraft(e));
            }
            if (!environmentRows.isEmpty()) environments.setSelectedIndex(selected);
            showEnvironment(environments.getSelectedValue()); baseline = draftState();
        } finally { updating = previous; }
    }
    public void apply() throws ConfigurationException {
        try {
            var data = new ControllerSettings.Data();
            data.selectedId = ControllerSettings.get(project).getState().selectedId;
            var names = new HashSet<String>();
            for (var draft : Collections.list(environmentRows.elements())) {
                var e = draft.environment();
                if (!names.add(e.name)) throw new IllegalArgumentException("环境名称不能重复。");
                e.validate(); data.environments.add(e);
            }
            if (data.environments.stream().noneMatch(e -> e.id.equals(data.selectedId)))
                data.selectedId = data.environments.isEmpty() ? "" : data.environments.getFirst().id;
            ControllerSettings.get(project).loadState(data); baseline = draftState();
        } catch (RuntimeException ex) { throw new ConfigurationException(ex.getMessage()); }
    }
    private static int number(String text, String label) {
        try { return Integer.parseInt(text.strip()); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException(label + "必须填写整数。"); }
    }
    private static final class EnvironmentDraft {
        String id, name, sdk, connection, host, port, timeout;
        final List<MappingDraft> mappings = new ArrayList<>();
        EnvironmentDraft(ControllerEnvironment e) {
            id = e.id; name = e.name; sdk = e.dllPath; connection = e.connection; host = e.host;
            port = Integer.toString(e.port); timeout = Integer.toString(e.timeoutMs);
            for (var m : e.mappings) mappings.add(new MappingDraft(m));
        }
        ControllerEnvironment environment() {
            var e = new ControllerEnvironment(); e.id = id; e.name = name.strip(); e.dllPath = sdk.strip();
            e.connection = connection; e.host = host.strip();
            e.port = number(port, "环境「" + e.name + "」的 TCP 端口"); e.timeoutMs = number(timeout, "环境「" + e.name + "」的超时");
            for (var m : mappings) e.mappings.add(new ControllerEnvironment.BufferMapping(m.file.strip(),
                number(m.buffer, "文件「" + m.file.strip() + "」的缓冲区编号")));
            return e;
        }
        public String toString() { return name.isBlank() ? "（未填写名称）" : name; }
    }
    private static final class MappingDraft {
        String file, buffer;
        MappingDraft(ControllerEnvironment.BufferMapping m) { file = m.file; buffer = Integer.toString(m.buffer); }
        public String toString() { return (file.isBlank() ? "（未选择文件）" : file) + " → " + buffer; }
    }
    private static final class DetailsPanel extends JPanel implements Scrollable {
        DetailsPanel() { super(new BorderLayout(0, 12)); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(16, (orientation == SwingConstants.VERTICAL ? visible.height : visible.width) - 16);
        }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getPreferredSize().height <= getParent().getHeight();
        }
    }
    public void disposeUIResources() {
        panel = null; environments = null; mappings = null; environmentRows = null; mappingRows = null;
        selectedEnvironment = null; selectedMapping = null;
        name = sdk = host = port = timeout = file = buffer = null; connection = null;
        removeEnvironment = addMapping = removeMapping = browseSdk = browseFile = null;
    }
}
