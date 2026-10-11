! Open this and function-library.acspl in the same IDE project.
! Verify cross-file function colors and GLOBAL/LOCAL variable colors.
GLOBAL REAL sharedSamples(3), result
LOCAL INT samples(3), grid(2)(3)
REAL implicitLocal

result = calculateValue(samples(0), grid(0)(0))
! Compute into result before DISP; do not write DISP calculateValue(1, 2).
DISP result
STOP

VOID consume(INT samples(), INT grid()(3)) {
    REAL result
    result = calculateValue(samples(0), grid(0)(0))
    ! result, samples and grid use LOCAL colors; sharedSamples uses GLOBAL colors.
    sharedSamples(0) = result
    RET
}
