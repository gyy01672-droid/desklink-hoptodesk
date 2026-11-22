[CmdletBinding()]
param([Parameter(Mandatory)][string]$Executable)

$ErrorActionPreference = 'Stop'
if ($env:GITHUB_ACTIONS -ne 'true' -or $env:RUNNER_OS -ne 'Windows' -or $env:RUNNER_ENVIRONMENT -ne 'github-hosted') {
    throw 'This UI smoke test runs in a disposable GitHub-hosted Windows job.'
}
$source = (Resolve-Path -LiteralPath $Executable).Path
$codeTree = (Get-Item -LiteralPath $source).Directory.Parent.Parent.FullName
# WireUI's WUIAR1 pack embeds uncompressed page files. Check the actual
# homepage and its scripts from this checkout before testing the window.
$latin1 = [Text.Encoding]::GetEncoding(28591)
$payload = $latin1.GetString([IO.File]::ReadAllBytes($source))
foreach ($resource in 'index.html','index.css','index.tis','common.css','common.tis','msgbox.tis','ab.tis','flags.tis','remote.html','remote.tis') {
    $bytes = [IO.File]::ReadAllBytes((Join-Path $codeTree "src/ui/$resource"))
    if ($bytes.Length -eq 0 -or -not $payload.Contains($latin1.GetString($bytes), [StringComparison]::Ordinal)) {
        throw "The executable is missing the checkout's UI resource: $resource"
    }
}
$payload = $null
$folder = Join-Path $env:RUNNER_TEMP ('desklink-ui-' + [Guid]::NewGuid().ToString('N'))
$appFolder = (New-Item -ItemType Directory -Path (Join-Path $folder 'bin') -Force).FullName
$exe = Join-Path $appFolder 'hoptodesk.exe'
Copy-Item -LiteralPath $source -Destination $exe

# On this disposable runner the sample reads only the client DC owned by the
# new process. Border, title bar and desktop are outside the sample rectangle.
Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;
public static class DeskLinkHomeCheck {
    private delegate bool Callback(IntPtr hwnd, IntPtr data);
    [StructLayout(LayoutKind.Sequential)] private struct Rect { public int left, top, right, bottom; }
    [DllImport("user32.dll")] private static extern bool EnumWindows(Callback cb, IntPtr unused);
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint pid);
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] private static extern int GetWindowText(IntPtr hwnd, StringBuilder text, int length);
    [DllImport("user32.dll")] private static extern bool GetClientRect(IntPtr hwnd, out Rect rect);
    [DllImport("user32.dll")] private static extern IntPtr GetDC(IntPtr hwnd);
    [DllImport("user32.dll")] private static extern int ReleaseDC(IntPtr hwnd, IntPtr dc);
    [DllImport("user32.dll")] private static extern bool IsWindowVisible(IntPtr hwnd);
    [DllImport("user32.dll")] private static extern bool ShowWindow(IntPtr hwnd, int cmd);
    [DllImport("gdi32.dll")] private static extern uint GetPixel(IntPtr dc, int x, int y);
    public static string Inspect(uint processId) {
        string result = null;
        EnumWindows(delegate(IntPtr hwnd, IntPtr unused) {
            uint owner;
            GetWindowThreadProcessId(hwnd, out owner);
            if (owner != processId) return true;
            var title = new StringBuilder(256);
            Rect bounds;
            GetWindowText(hwnd, title, title.Capacity);
            if (title.ToString().IndexOf("HopToDesk", StringComparison.OrdinalIgnoreCase) < 0 ||
                !GetClientRect(hwnd, out bounds) || bounds.right < 300 || bounds.bottom < 190) return true;
            if (!IsWindowVisible(hwnd)) { ShowWindow(hwnd, 4); return true; }
            var dc = GetDC(hwnd);
            if (dc == IntPtr.Zero) return true;
            int count = 0, peak = 0;
            var colors = new Dictionary<uint, int>();
            try {
                for (int row = 1; row < 49; ++row) {
                    for (int col = 1; col < 65; ++col) {
                        uint pixel = GetPixel(dc, 8 + (bounds.right - 16) * col / 65,
                                              8 + (bounds.bottom - 16) * row / 49);
                        if (pixel == 0xffffffff) continue;
                        int hits; colors.TryGetValue(pixel, out hits);
                        colors[pixel] = ++hits;
                        peak = Math.Max(peak, hits);
                        ++count;
                    }
                }
            } finally { ReleaseDC(hwnd, dc); }
            if (count < 2000 || colors.Count < 12 || count - peak < 80) return true;
            result = String.Format("window={0}x{1}, sampled={2}, colors={3}, nonuniform={4}",
                                   bounds.right, bounds.bottom, count, colors.Count, count - peak);
            return false;
        }, IntPtr.Zero);
        return result;
    }
}
'@

$rule = 'DeskLink-UI-test-' + [Guid]::NewGuid().ToString('N')
$child = $null
try {
    New-NetFirewallRule -Name $rule -DisplayName $rule -Program $exe -Direction Outbound -Action Block -Profile Any | Out-Null
    $start = [Diagnostics.ProcessStartInfo]::new($exe, '--no-server')
    $start.WorkingDirectory = $appFolder
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    foreach ($key in 'APPDATA', 'LOCALAPPDATA', 'TEMP', 'TMP') {
        $path = (New-Item -ItemType Directory -Path (Join-Path $folder $key) -Force).FullName
        $start.Environment[$key] = $path
    }
    # CPU presentation uses GDI on Windows and allows testing runners without a GPU.
    $start.Environment['WIREUI_FORCE_CPU'] = '1'
    $child = [Diagnostics.Process]::Start($start)
    $clock = [Diagnostics.Stopwatch]::StartNew()
    while ($clock.Elapsed.TotalSeconds -lt 40) {
        if ($child.HasExited) { throw "The independent EXE closed before rendering (exit $($child.ExitCode))." }
        $render = [DeskLinkHomeCheck]::Inspect([uint32]$child.Id)
        if ($render) {
            Write-Output "Offline home window rendered from the single native EXE. $render"
            return
        }
        Start-Sleep -Milliseconds 400
    }
    throw 'The detached EXE did not draw a nonempty HopToDesk client area in 40 seconds.'
} finally {
    if ($null -ne $child) {
        if (-not $child.HasExited) { $child.Kill($true); $child.WaitForExit(10000) | Out-Null }
        $child.Dispose()
    }
    Get-NetFirewallRule -Name $rule -ErrorAction SilentlyContinue | Remove-NetFirewallRule
}
