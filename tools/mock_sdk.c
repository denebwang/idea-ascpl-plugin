/* Test-only SDK ABI fixture. Does not implement ACS motion control and is never shipped. */
#include <windows.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#define API __declspec(dllexport)
static char program[1024*1024];
static char flashProgram[sizeof(program)];
static int flashLength, flashSaveCount, flashBuffer = -1;
static int length, errorCode, compiled, state, currentLine = 1;
API HANDLE acsc_OpenCommSimulator(void) { return (HANDLE)1; }
API HANDLE acsc_OpenCommEthernetTCP(char *host, int port) { return (HANDLE)1; }
API int acsc_CloseComm(HANDLE handle) { return 1; }
API int acsc_SetTimeout(HANDLE handle, int timeout) { return 1; }
API int acsc_GetLastError(void) { return errorCode; }
API int acsc_GetErrorString(HANDLE handle, int code, char *out, int count, int *received) {
    const char *text = "mock SDK error";
    *received = (int)strlen(text); memcpy(out, text, *received); return 1;
}
API int acsc_LoadBuffer(HANDLE handle, int buffer, char *text, int count, void *wait) {
    if (count > sizeof(program)) { errorCode = 400; return 0; }
    memcpy(program, text, count); length = count; compiled = 0; state = 0; return 1;
}
API int acsc_UploadBuffer(HANDLE handle, int buffer, int offset, char *text, int count, int *received, void *wait) {
    int size = length-offset;
    if (size < 0) size = 0;
    if (size > count) size = count;
    memcpy(text, program+offset, size); *received = size; return 1;
}
API int acsc_Transaction(HANDLE handle, char *out, int outCount, char *in, int inCount, int *received, void *wait) {
    char command[1024], response[2048] = "";
    if (outCount > 1023 || outCount < 1 || out[outCount-1] != '\r') { errorCode = 401; return 0; }
    memcpy(command, out, outCount); command[outCount-1] = 0;
    if (!strcmp(command, "?FAIL")) { errorCode = 402; return 0; }
    /* Only the explicitly addressed test buffer is supported; reject broad saves. */
    if (!strncmp(command, "#SAVE", 5) && strcmp(command, "#SAVEPROG 2")) { errorCode = 404; return 0; }
    if (!strcmp(command, "#SI")) strcpy(response, "Controller Firmware Version:3.12.01.00\r\n");
    else if (!strcmp(command, "#SAVEPROG 2")) {
        memcpy(flashProgram, program, length); flashLength = length; flashBuffer = 2; flashSaveCount++;
    }
    /* Fixture-only inspection commands; these are not ACS controller commands. */
    else if (!strcmp(command, "?MOCK_FLASH_SAVE_COUNT")) sprintf(response, "%d\r\n", flashSaveCount);
    else if (!strcmp(command, "?MOCK_FLASH_BUFFER")) sprintf(response, "%d\r\n", flashBuffer);
    else if (!strcmp(command, "?MOCK_FLASH_LENGTH")) sprintf(response, "%d\r\n", flashLength);
    else if (!strcmp(command, "?MOCK_FLASH_MATCH")) sprintf(response, "%d\r\n",
        flashLength == length && !memcmp(flashProgram, program, length));
    else if (!strcmp(command, "?2")) {
        if (state == 2) sprintf(response, "Buffer 2: 8 lines, suspended in line %d\r\n", currentLine);
        else sprintf(response, "Buffer 2: 8 lines, %s\r\n", state == 1 ? "running" : compiled ? "compiled" : "not compiled");
    }
    else if (!strcmp(command, "#2C")) { compiled = 1; strcpy(response, "8 lines compiled\r\n"); }
    else if (!strcmp(command, "#2XD")) { state = 2; currentLine = 3; }
    else if (!strcmp(command, "#2XS")) { state = 2; currentLine++; }
    else if (!strcmp(command, "#2X")) state = 1;
    else if (!strcmp(command, "#2P")) state = 2;
    else if (!strcmp(command, "#2S")) state = 0;
    else if (command[0] == '?') strcpy(response, "42\r\n");
    *received = (int)strlen(response);
    if (*received > inCount) { errorCode = 403; return 0; }
    memcpy(in, response, *received); return 1;
}
