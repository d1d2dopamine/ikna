# Read-only Windows probe. No hooks, window changes, downloads or elevation.
# Run while the intended Ikna window is open and restored, not minimized.
# See diagnose-window-minimize-ru.txt. This diagnoses; it does not fix animation.
[CmdletBinding()]
param([int]$IknaProcessId = 0)

$ErrorActionPreference = 'Stop'
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    throw 'This probe requires Windows.'
}

# List only windows titled Ikna; do not collect command lines or other app data.
if ($IknaProcessId -eq 0) {
    $candidates = @(Get-Process | Where-Object {
        $_.MainWindowHandle -ne [IntPtr]::Zero -and
        $_.MainWindowTitle -match '^Ikna(?:\s|$)'
    })
    if ($candidates.Count -eq 0) {
        throw 'No open Ikna window found. Restore the intended Ikna window and retry.'
    }
    $candidates | Select-Object Id, ProcessName, MainWindowTitle | Format-Table | Out-Host
    $answer = Read-Host 'Enter the PID of the intended Ikna window from this list'
    if (-not [int]::TryParse($answer, [ref]$IknaProcessId) -or $IknaProcessId -le 0) {
        throw 'A positive numeric PID is required. Nothing was changed.'
    }
    if ($IknaProcessId -notin $candidates.Id) {
        throw 'The selected PID was not in the Ikna list. Nothing was changed.'
    }
}
if ($IknaProcessId -le 0) { throw 'A positive numeric PID is required.' }
$target = Get-Process -Id $IknaProcessId
$target.Refresh()
$handle = $target.MainWindowHandle
if ($handle -eq [IntPtr]::Zero -or $target.MainWindowTitle -notmatch '^Ikna(?:\s|$)') {
    throw 'This PID does not have an open Ikna main window. Restore Ikna and retry.'
}

if (-not ('IknaReadOnlyWindowProbeV1' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;

public static class IknaReadOnlyWindowProbeV1 {
    [StructLayout(LayoutKind.Sequential)]
    public struct AnimationInfo {
        public uint cbSize;
        public int iMinAnimate;
    }
    [DllImport("user32.dll", EntryPoint="GetWindowLongPtrW", SetLastError=true)]
    private static extern IntPtr GetWindowLongPtr64(IntPtr hwnd, int index);
    [DllImport("user32.dll", EntryPoint="GetWindowLongW", SetLastError=true)]
    private static extern int GetWindowLong32(IntPtr hwnd, int index);
    // Reset only this thread's diagnostic error slot, never a window setting.
    [DllImport("kernel32.dll", EntryPoint="SetLastError")]
    private static extern void ClearLastError(uint error);
    [DllImport("user32.dll", SetLastError=true)]
    private static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint pid);
    [DllImport("user32.dll", EntryPoint="SystemParametersInfoW", SetLastError=true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool SystemParametersInfo(uint action, uint param,
        ref AnimationInfo info, uint flags);

    public static uint Owner(IntPtr hwnd) {
        uint pid;
        if (GetWindowThreadProcessId(hwnd, out pid) == 0)
            throw new Win32Exception(Marshal.GetLastWin32Error());
        return pid;
    }
    public static uint Style(IntPtr hwnd, int index) {
        ClearLastError(0);
        long value = IntPtr.Size == 8
            ? GetWindowLongPtr64(hwnd, index).ToInt64()
            : GetWindowLong32(hwnd, index);
        int error = Marshal.GetLastWin32Error();
        if (value == 0 && error != 0) throw new Win32Exception(error);
        return unchecked((uint)value);
    }
    public static int MinAnimation() {
        AnimationInfo info = new AnimationInfo();
        info.cbSize = (uint)Marshal.SizeOf(typeof(AnimationInfo));
        // SPI_GETANIMATION = 0x0048. Never SPI_SETANIMATION.
        if (!SystemParametersInfo(0x0048, info.cbSize, ref info, 0))
            throw new Win32Exception(Marshal.GetLastWin32Error());
        return info.iMinAnimate;
    }
}
'@
}

if ([IknaReadOnlyWindowProbeV1]::Owner($handle) -ne $IknaProcessId) {
    throw 'The window changed owner. Retry with the current Ikna PID.'
}
$style = [IknaReadOnlyWindowProbeV1]::Style($handle, -16)
$exStyle = [IknaReadOnlyWindowProbeV1]::Style($handle, -20)
$minAnimation = [IknaReadOnlyWindowProbeV1]::MinAnimation()
if ([IknaReadOnlyWindowProbeV1]::Owner($handle) -ne $IknaProcessId) {
    throw 'The window changed during the read. Discard this sample and retry.'
}

'Ikna minimize probe v1 (read-only; not an animation test or fix)'
'PID={0}; HWND=0x{1:X}; PowerShellBits={2}' -f $IknaProcessId, $handle.ToInt64(), ([IntPtr]::Size * 8)
'GWL_STYLE=0x{0:X8}; GWL_EXSTYLE=0x{1:X8}' -f $style, $exStyle
$styleFlags = [ordered]@{
    WS_CAPTION=0x00C00000; WS_THICKFRAME=0x00040000
    WS_SYSMENU=0x00080000; WS_MINIMIZEBOX=0x00020000
    WS_MAXIMIZEBOX=0x00010000; WS_POPUP=[uint32]2147483648
    WS_MINIMIZE=0x20000000; WS_MAXIMIZE=0x01000000; WS_VISIBLE=0x10000000
}
$exFlags = [ordered]@{
    WS_EX_LAYERED=0x00080000; WS_EX_TOOLWINDOW=0x00000080
    WS_EX_APPWINDOW=0x00040000; WS_EX_NOREDIRECTIONBITMAP=0x00200000
}
foreach ($flag in $styleFlags.GetEnumerator()) {
    '{0}={1}' -f $flag.Key, (($style -band [uint32]$flag.Value) -eq [uint32]$flag.Value)
}
foreach ($flag in $exFlags.GetEnumerator()) {
    '{0}={1}' -f $flag.Key, (($exStyle -band [uint32]$flag.Value) -eq [uint32]$flag.Value)
}
'SPI_GETANIMATION.iMinAnimate={0}; minimizeRestoreAnimationEnabled={1}' -f $minAnimation, ($minAnimation -ne 0)
'No settings changed. Copy this output plus the two manual observations from the instructions.'
