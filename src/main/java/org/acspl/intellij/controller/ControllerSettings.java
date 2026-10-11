package org.acspl.intellij.controller;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import java.util.*;

@State(name = "AcsplControllerEnvironments", storages = @Storage("acspl-controller.xml"))
public final class ControllerSettings implements PersistentStateComponent<ControllerSettings.Data> {
    public static final class Data {
        public String selectedId = "";
        public List<ControllerEnvironment> environments = new ArrayList<>();
    }
    private Data data = new Data();
    public static ControllerSettings get(Project project) { return project.getService(ControllerSettings.class); }
    public @NotNull Data getState() { return data; }
    public void loadState(@NotNull Data state) { data = state; }
    public ControllerEnvironment selected() {
        return data.environments.stream().filter(e -> e.id.equals(data.selectedId)).findFirst()
            .orElse(data.environments.isEmpty() ? null : data.environments.getFirst());
    }
}
