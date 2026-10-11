package org.acspl.intellij.controller.debug;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.breakpoints.*;
import org.acspl.intellij.AcsplFileType;
import java.util.EnumSet;

public final class AcsplBreakpointType extends XLineBreakpointType<AcsplBreakpointProperties> {
    public AcsplBreakpointType() { super("acspl-line", "ACSPL+ line breakpoint"); }
    public AcsplBreakpointProperties createBreakpointProperties(VirtualFile file, int line) { return new AcsplBreakpointProperties(); }
    public AcsplBreakpointProperties createProperties() { return new AcsplBreakpointProperties(); }
    public boolean canPutAt(VirtualFile file, int line, Project project) { return file.getFileType() == AcsplFileType.INSTANCE && line >= 0; }
    public EnumSet<StandardPanels> getVisibleStandardPanels() { return EnumSet.noneOf(StandardPanels.class); }
}
