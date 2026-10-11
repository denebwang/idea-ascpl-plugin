! ACSPL+ 3.12 editing demonstration - do not run on a controller unreviewed.
! Verify highlighting, completion, comments and offline documentation.
GLOBAL REAL destination
LOCAL INT counter, samples(3) = (10, 20, 30)
AXISDEF X = 0, Y = 1

VOID showPosition(INT axis)

AUTOEXEC:
destination = SIN(1.25e-3) * 100
counter = 0x10 + 0b111
samples(0) = 'A'

! Ctrl+Q on PTP, DISP, FPOS0 or #MOVE opens the manual-backed documentation.
ENABLE X
PTP/rw X, destination
GO X
TILL ^MST(0).#MOVE
DISP "Position ! %f\n", FPOS0
counter = IN0.4
counter = counter / 2
showPosition(X)
STOP

VOID showPosition(INT axis) {
    REAL currentPosition
    currentPosition = FPOS(axis)
    DISP "Axis %d position %f", axis, currentPosition
    RET
}
