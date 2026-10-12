# ACS SDK Windows x64 adapter. SDK binaries are supplied by the user, never bundled.
# The JSON stream transports program bytes as base64, independently of console encoding.
$ErrorActionPreference = 'Stop'
[Console]::InputEncoding = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$nativeSource = @'
using System;
using System.IO;
using System.Text;
using System.Runtime.InteropServices;
using System.Diagnostics;
public sealed class AcsSdk : IDisposable {
    [DllImport("kernel32", CharSet=CharSet.Unicode, SetLastError=true)]
    static extern IntPtr LoadLibraryEx(string path, IntPtr reserved, uint flags);
    [DllImport("kernel32", CharSet=CharSet.Ansi, SetLastError=true)]
    static extern IntPtr GetProcAddress(IntPtr library, string name);
    [DllImport("kernel32")] static extern bool FreeLibrary(IntPtr library);
    [UnmanagedFunctionPointer(CallingConvention.Winapi)] delegate IntPtr OpenSimulator();
    [UnmanagedFunctionPointer(CallingConvention.Winapi)] delegate IntPtr OpenEthernet([MarshalAs(UnmanagedType.LPStr)] string host, int port);
    [UnmanagedFunctionPointer(CallingConvention.Winapi)] delegate int CloseComm(IntPtr handle);
    [UnmanagedFunctionPointer(CallingConvention.Winapi)] delegate int SetTimeout(IntPtr handle, int milliseconds);
    [UnmanagedFunctionPointer(CallingConvention.Winapi)] delegate int LastError();
    [UnmanagedFunctionPointer(CallingConvention.Winapi)] delegate int ErrorString(IntPtr handle, int code, [Out] byte[] text, int count, out int received);
    [UnmanagedFunctionPointer(CallingConvention.Winapi)] delegate int Transaction(IntPtr handle, [In] byte[] output, int outputCount, [Out] byte[] input, int inputCount, out int received, IntPtr wait);
    [UnmanagedFunctionPointer(CallingConvention.Winapi)] delegate int LoadBuffer(IntPtr handle, int buffer, [In] byte[] program, int count, IntPtr wait);
    [UnmanagedFunctionPointer(CallingConvention.Winapi)] delegate int UploadBuffer(IntPtr handle, int buffer, int offset, [Out] byte[] program, int count, out int received, IntPtr wait);
    // ACS communication handles are opaque channel values, not Win32 object handles.
    // The SDK uses ACSC_INVALID (-1) for failure; a value of zero is valid.
    static readonly IntPtr InvalidHandle = new IntPtr(-1);
    IntPtr library, handle = InvalidHandle;
    OpenSimulator simulator; OpenEthernet ethernet; CloseComm close; SetTimeout timeout;
    LastError lastError; ErrorString errorString; Transaction transaction; LoadBuffer load; UploadBuffer upload;
    public string Version;
    T Export<T>(string name) where T : class {
        IntPtr p = GetProcAddress(library, name);
        if (p == IntPtr.Zero) throw new InvalidOperationException("Missing ACS SDK export: " + name);
        return Marshal.GetDelegateForFunctionPointer(p, typeof(T)) as T;
    }
    public AcsSdk(string path) {
        if (IntPtr.Size != 8) throw new InvalidOperationException("A 64-bit Windows PowerShell process is required.");
        path = Path.GetFullPath(path);
        using (var f = new BinaryReader(File.OpenRead(path))) {
            if (f.ReadUInt16() != 0x5a4d) throw new InvalidOperationException("Not a Windows SDK DLL.");
            f.BaseStream.Position = 0x3c;
            int offset = f.ReadInt32();
            f.BaseStream.Position = offset;
            if (f.ReadUInt32() != 0x4550 || f.ReadUInt16() != 0x8664)
                throw new InvalidOperationException("Select the Windows x64 ACS SDK DLL; 32-bit DLLs are not supported.");
        }
        Version = FileVersionInfo.GetVersionInfo(path).FileVersion ?? "";
        // C library file versions have their own numbering; they are not ADK/firmware versions.
        // ControllerEngine checks the controller firmware separately using #SI.
        // Search dependencies next to the DLL and in installed system directories, not the working directory.
        library = LoadLibraryEx(path, IntPtr.Zero, 0x100 | 0x1000);
        if (library == IntPtr.Zero) throw new InvalidOperationException("Cannot load ACS SDK DLL/dependencies; Windows error " + Marshal.GetLastWin32Error());
        try {
            simulator = Export<OpenSimulator>("acsc_OpenCommSimulator");
            ethernet = Export<OpenEthernet>("acsc_OpenCommEthernetTCP");
            close = Export<CloseComm>("acsc_CloseComm");
            timeout = Export<SetTimeout>("acsc_SetTimeout");
            lastError = Export<LastError>("acsc_GetLastError");
            errorString = Export<ErrorString>("acsc_GetErrorString");
            transaction = Export<Transaction>("acsc_Transaction");
            load = Export<LoadBuffer>("acsc_LoadBuffer");
            upload = Export<UploadBuffer>("acsc_UploadBuffer");
        } catch { Dispose(); throw; }
    }
    void Check(int result, string operation) {
        if (result != 0) return;
        int code = lastError(); byte[] text = new byte[2048]; int received;
        string detail = code == 0 ? "The SDK returned failure but reported no error code." : "Error description unavailable.";
        if (code != 0 && errorString(handle, code, text, text.Length, out received) != 0) {
            string description = Encoding.Default.GetString(text, 0, Math.Max(0, Math.Min(received, text.Length))).TrimEnd('\0', '\r', '\n');
            if (!String.IsNullOrWhiteSpace(description)) detail = description;
        }
        throw new InvalidOperationException(operation + " failed (ACS SDK error " + code + "): " + detail);
    }
    void Connected() { if (handle == InvalidHandle) throw new InvalidOperationException("Not connected."); }
    static void Buffer(int b) { if (b < 0 || b > 63) throw new ArgumentOutOfRangeException("buffer"); }
    public string Connect(string kind, string host, int port, int milliseconds) {
        handle = kind == "SIMULATOR" ? simulator() : ethernet(host, port);
        if (handle == InvalidHandle)
            Check(0, kind == "SIMULATOR" ? "acsc_OpenCommSimulator" : "acsc_OpenCommEthernetTCP (" + host + ":" + port + ")");
        Check(timeout(handle, milliseconds), "acsc_SetTimeout");
        return "ACS C Library " + (Version.Length == 0 ? "(file version metadata unavailable)" : Version) + " / " + kind;
    }
    public string Query(string command) {
        Connected();
        if (command.IndexOfAny(new char[]{'\r','\n','\0'}) >= 0) throw new InvalidOperationException("Invalid command.");
        byte[] output = Encoding.ASCII.GetBytes(command + "\r");
        byte[] input = new byte[65536]; int received;
        Check(transaction(handle, output, output.Length, input, input.Length, out received, IntPtr.Zero), "acsc_Transaction");
        if (received < 0 || received > input.Length) throw new InvalidOperationException("Invalid SDK reply length.");
        return Encoding.Default.GetString(input, 0, received).TrimEnd('\0');
    }
    public void Load(int buffer, byte[] program) {
        Connected(); Buffer(buffer);
        if (program.Length > 16*1024*1024 || Array.IndexOf(program, (byte)0) >= 0) throw new InvalidOperationException("Invalid program bytes.");
        foreach (byte value in program)
            if (value > 0x7f) throw new InvalidOperationException("ACSPL+ source must contain ASCII bytes only.");
        // Explicit count excludes the C string terminator.
        byte[] terminated = new byte[program.Length + 1]; Array.Copy(program, terminated, program.Length);
        Check(load(handle, buffer, terminated, program.Length, IntPtr.Zero), "acsc_LoadBuffer");
    }
    public byte[] Upload(int buffer) {
        Connected(); Buffer(buffer);
        using (var result = new MemoryStream()) {
            const int chunk = 16384;
            for (int offset = 0; offset < 16*1024*1024; offset += chunk) {
                byte[] bytes = new byte[chunk]; int received;
                Check(upload(handle, buffer, offset, bytes, chunk, out received, IntPtr.Zero), "acsc_UploadBuffer");
                if (received < 0 || received > chunk) throw new InvalidOperationException("Invalid SDK upload length.");
                // SDK versions may count a terminating NUL as part of the final reply.
                int count = received;
                if (count > 0 && bytes[count-1] == 0) count--;
                result.Write(bytes, 0, count);
                if (received < chunk || count < received) return result.ToArray();
            }
            throw new InvalidOperationException("Program exceeds 16 MiB upload limit.");
        }
    }
    public void Dispose() {
        if (close != null && handle != InvalidHandle) close(handle);
        handle = InvalidHandle;
        if (library != IntPtr.Zero) FreeLibrary(library);
        library = IntPtr.Zero;
    }
}
'@
$sdk = $null
try {
    Add-Type -TypeDefinition $nativeSource
    while ($null -ne ($line = [Console]::ReadLine())) {
        $request = $null
        try {
            $request = $line | ConvertFrom-Json
            $result = ''
            switch ($request.op) {
                'connect' {
                    if ($null -ne $sdk) { throw 'Already connected.' }
                    $sdk = New-Object AcsSdk([string]$request.dll)
                    $result = $sdk.Connect([string]$request.connection, [string]$request.host, [int]$request.port, [int]$request.timeout)
                }
                'transaction' { $result = $sdk.Query([string]$request.command) }
                'upload' { $result = [Convert]::ToBase64String($sdk.Upload([int]$request.buffer)) }
                'load' { $sdk.Load([int]$request.buffer, [Convert]::FromBase64String([string]$request.program)) }
                default { throw 'Unknown SDK operation.' }
            }
            $reply = @{ id=$request.id; ok=$true; result=$result }
        } catch {
            $reply = @{ id=$request.id; ok=$false; error=$_.Exception.GetBaseException().Message }
        }
        [Console]::WriteLine('ACSPL_JSON:' + ($reply | ConvertTo-Json -Compress -Depth 6))
    }
} catch {
    [Console]::WriteLine('ACSPL_JSON:' + (@{ ok=$false; error=$_.Exception.GetBaseException().Message } | ConvertTo-Json -Compress))
} finally {
    if ($null -ne $sdk) { $sdk.Dispose() }
}
