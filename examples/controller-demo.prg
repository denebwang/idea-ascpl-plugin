! Simulator demonstration: no axis or I/O commands.
! Map this file to a spare buffer, compile, then put a breakpoint on demoCounter assignment.
LOCAL INT demoCounter
demoCounter = 0
LOOP 10
    demoCounter = demoCounter + 1
    WAIT 100
END
STOP
