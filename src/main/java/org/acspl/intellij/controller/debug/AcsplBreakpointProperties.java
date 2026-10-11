package org.acspl.intellij.controller.debug;

import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import org.jetbrains.annotations.NotNull;

public final class AcsplBreakpointProperties extends XBreakpointProperties<AcsplBreakpointProperties> {
    public AcsplBreakpointProperties getState() { return this; }
    public void loadState(@NotNull AcsplBreakpointProperties state) {}
}
