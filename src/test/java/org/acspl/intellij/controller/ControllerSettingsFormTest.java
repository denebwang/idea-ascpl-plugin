package org.acspl.intellij.controller;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ControllerSettingsFormTest extends BasePlatformTestCase {
    public void testModificationPollingPreservesInputAndCaret() throws Exception {
        withSettings(data(), (configurable, root) -> {
            assertFalse(contains(root, JTable.class));
            assertFalse(configurable.isModified());
            var sdk = named(root, "sdk", JTextField.class);
            sdk.setText("C:/SDK with spaces/acs\u4e2d\u6587.dll"); sdk.setCaretPosition(5); sdk.moveCaretPosition(12);
            var document = sdk.getDocument(); var events = new AtomicInteger();
            document.addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { events.incrementAndGet(); }
                public void removeUpdate(DocumentEvent e) { events.incrementAndGet(); }
                public void changedUpdate(DocumentEvent e) { events.incrementAndGet(); }
            });
            for (int i = 0; i < 200; i++) assertTrue(configurable.isModified());
            assertSame(document, sdk.getDocument()); assertEquals(0, events.get());
            assertEquals("C:/SDK with spaces/acs\u4e2d\u6587.dll", sdk.getText());
            assertEquals(12, sdk.getCaret().getDot()); assertEquals(5, sdk.getCaret().getMark());
            assertEquals(0, named(root, "environments", JList.class).getSelectedIndex());
            assertEquals("demo.prg", named(root, "file", JTextField.class).getText());
            assertFalse(ControllerManager.get(getProject()).connected());
        });
    }
    public void testRenameKeepsMappingsAndApplyPreservesIdsAndSelection() throws Exception {
        var original = data(); var second = ControllerEngineTest.environment(); second.name = "Controller B";
        original.environments.add(second); original.selectedId = second.id;
        withSettings(original, (configurable, root) -> {
            named(root, "environments", JList.class).setSelectedIndex(0);
            named(root, "name", JTextField.class).setText("Renamed controller");
            named(root, "sdk", JTextField.class).setText("C:/Program Files/ACS SDK/acsc64.dll");
            named(root, "connection", JComboBox.class).setSelectedItem("ETHERNET");
            named(root, "file", JTextField.class).setText("programs/demo.prg");
            named(root, "buffer", JTextField.class).setText("5");
            assertEquals("Controller A", original.environments.getFirst().name);
            assertEquals("demo.prg", original.environments.getFirst().mappings.getFirst().file);
            configurable.apply(); assertFalse(configurable.isModified());
            var saved = ControllerSettings.get(getProject()).getState();
            assertEquals(second.id, saved.selectedId); assertEquals(2, saved.environments.size());
            var first = saved.environments.getFirst();
            assertEquals(original.environments.getFirst().id, first.id); assertEquals("Renamed controller", first.name);
            assertEquals("C:/Program Files/ACS SDK/acsc64.dll", first.dllPath); assertEquals("ETHERNET", first.connection);
            assertEquals("programs/demo.prg", first.mappings.getFirst().file); assertEquals(5, first.mappings.getFirst().buffer);
            assertEquals(second.id, saved.environments.get(1).id);
            assertEquals("demo.prg", saved.environments.get(1).mappings.getFirst().file);
            assertEquals(2, saved.environments.get(1).mappings.getFirst().buffer);
            assertFalse(ControllerManager.get(getProject()).connected());
            if (Boolean.parseBoolean(System.getenv("ACSPL_SETTINGS_PREVIEW"))) {
                preview(root, 1000, 520, "controller-settings-form.png");
                preview(root, 820, 520, "controller-settings-form-small.png");
            }
        });
    }
    public void testSwitchingSelectionsKeepsIncompleteDraftsAndResetCancelsThem() throws Exception {
        var original = data(); var second = ControllerEngineTest.environment(); second.name = "Controller B";
        original.environments.add(second);
        withSettings(original, (configurable, root) -> {
            var environments = named(root, "environments", JList.class);
            named(root, "port", JTextField.class).setText("-");
            named(root, "buffer", JTextField.class).setText("");
            environments.setSelectedIndex(1);
            named(root, "name", JTextField.class).setText("Second draft");
            environments.setSelectedIndex(0);
            assertEquals("-", named(root, "port", JTextField.class).getText());
            assertEquals("", named(root, "buffer", JTextField.class).getText());
            var error = org.junit.Assert.assertThrows(ConfigurationException.class, configurable::apply);
            assertTrue(error.getMessage().contains("TCP"));
            assertSame(original, ControllerSettings.get(getProject()).getState());
            assertEquals("-", named(root, "port", JTextField.class).getText()); assertTrue(configurable.isModified());
            configurable.reset(); assertFalse(configurable.isModified());
            assertEquals("701", named(root, "port", JTextField.class).getText());
            assertEquals("2", named(root, "buffer", JTextField.class).getText());
            environments.setSelectedIndex(1);
            assertEquals("Controller B", named(root, "name", JTextField.class).getText());
            assertFalse(configurable.isModified());
        });
    }
    public void testAddingRemovingAndValidatingPerEnvironmentMappings() throws Exception {
        withSettings(new ControllerSettings.Data(), (configurable, root) -> {
            var environments = named(root, "environments", JList.class);
            var mappings = named(root, "mappings", JList.class);
            assertFalse(named(root, "sdk", JTextField.class).isEnabled());
            named(root, "addEnvironment", JButton.class).doClick();
            named(root, "name", JTextField.class).setText("First");
            named(root, "sdk", JTextField.class).setText("C:/SDK/acsc64.dll");
            named(root, "addMapping", JButton.class).doClick();
            org.junit.Assert.assertThrows(ConfigurationException.class, configurable::apply);
            named(root, "file", JTextField.class).setText("demo.prg");
            named(root, "buffer", JTextField.class).setText("2"); configurable.apply();
            named(root, "addMapping", JButton.class).doClick();
            named(root, "file", JTextField.class).setText("other.prg");
            named(root, "buffer", JTextField.class).setText("2");
            org.junit.Assert.assertThrows(ConfigurationException.class, configurable::apply);
            assertEquals(1, ControllerSettings.get(getProject()).selected().mappings.size());
            named(root, "buffer", JTextField.class).setText("0"); configurable.apply();
            named(root, "addEnvironment", JButton.class).doClick();
            assertEquals(0, mappings.getModel().getSize());
            named(root, "name", JTextField.class).setText("Second");
            named(root, "sdk", JTextField.class).setText("C:/SDK/other64.dll");
            named(root, "addMapping", JButton.class).doClick();
            named(root, "file", JTextField.class).setText("demo.prg");
            named(root, "buffer", JTextField.class).setText("2"); configurable.apply();
            var saved = ControllerSettings.get(getProject()).getState();
            assertEquals(2, saved.environments.size()); assertEquals(2, saved.environments.getFirst().mappings.size());
            var secondId = saved.environments.get(1).id;
            assertFalse(saved.environments.getFirst().id.equals(secondId));
            environments.setSelectedIndex(0); named(root, "removeEnvironment", JButton.class).doClick(); configurable.apply();
            saved = ControllerSettings.get(getProject()).getState();
            assertEquals(secondId, saved.selectedId); assertEquals(1, saved.environments.size());
            assertEquals("demo.prg", saved.environments.getFirst().mappings.getFirst().file);
            named(root, "removeMapping", JButton.class).doClick(); configurable.apply();
            assertTrue(ControllerSettings.get(getProject()).selected().mappings.isEmpty());
            named(root, "removeEnvironment", JButton.class).doClick(); configurable.apply();
            assertTrue(ControllerSettings.get(getProject()).getState().environments.isEmpty());
            assertEquals("", ControllerSettings.get(getProject()).getState().selectedId); assertFalse(configurable.isModified());
            assertFalse(named(root, "sdk", JTextField.class).isEnabled());
            assertFalse(ControllerManager.get(getProject()).connected());
        });
    }
    private static ControllerSettings.Data data() {
        var data = new ControllerSettings.Data(); var e = ControllerEngineTest.environment(); e.name = "Controller A";
        data.environments.add(e); data.selectedId = e.id; return data;
    }
    private void withSettings(ControllerSettings.Data data, FormCheck check) throws Exception {
        var settings = ControllerSettings.get(getProject()); var previous = settings.getState();
        var configurable = new ControllerConfigurable(getProject());
        try { settings.loadState(data); check.run(configurable, configurable.createComponent()); }
        finally { configurable.disposeUIResources(); settings.loadState(previous); }
    }
    @FunctionalInterface private interface FormCheck {
        void run(ControllerConfigurable configurable, JComponent root) throws Exception;
    }
    private static <T extends Component> T named(Component root, String id, Class<T> type) {
        var result = find(root, "controller." + id, type); assertNotNull("Missing form component: " + id, result); return result;
    }
    private static <T extends Component> T find(Component root, String name, Class<T> type) {
        if (name.equals(root.getName()) && type.isInstance(root)) return type.cast(root);
        if (root instanceof Container c) for (var child : c.getComponents()) {
            var result = find(child, name, type); if (result != null) return result;
        }
        return null;
    }
    private static boolean contains(Component root, Class<?> type) {
        if (type.isInstance(root)) return true;
        if (root instanceof Container c) for (var child : c.getComponents()) if (contains(child, type)) return true;
        return false;
    }
    private static void preview(JComponent root, int width, int height, String name) throws Exception {
        root.setSize(width, height); layout(root);
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB); var graphics = image.createGraphics();
        try { root.printAll(graphics); } finally { graphics.dispose(); }
        var path = Path.of("build", "tmp", name); Files.createDirectories(path.getParent());
        javax.imageio.ImageIO.write(image, "png", path.toFile());
    }
    private static void layout(Container c) {
        c.doLayout(); for (var child : c.getComponents()) if (child instanceof Container next) layout(next);
    }
}
