# Windows installer

`ikna.nsi` is the complete NSIS recipe used by CI. It is committed so the
installer is reviewable: there is no downloaded template or hidden packaging
step.

## What it does

- installs the jpackage application image for the current user in
  `%LOCALAPPDATA%\Programs\ikna`;
- creates Start-menu and Desktop shortcuts by default;
- registers one entry in **Installed apps** under `HKCU`;
- installs no service, scheduled task, browser item or automatic startup entry;
- updates an existing NSIS installation only when its private
  `.ikna-install-root` marker is present;
- refuses to recursively replace or remove a non-empty unmarked folder;
- refuses installation and removal while `Ikna.exe` is running.

The visible pages use native NSIS controls for keyboard, DPI and screen-reader
support. `sidebar.bmp` and `header.bmp` are generated from the same wordmark and
default Ink palette as the application by `tools/make-installer-art.py`.

## Silent use

```powershell
ikna-v0.10.0-press-windows-x64-setup.exe /S
```

NSIS also accepts `/D=C:\path\to\ikna` as the final argument. Silent installation
returns `0` on success, `32` when ikna is open, `13` for an unsafe destination,
and `5` when an old managed application directory cannot be removed.

Ordinary silent removal cleans the program directory, shortcuts and Windows
registration while preserving cards and settings:

```powershell
"$env:LOCALAPPDATA\Programs\ikna\Uninstall.exe" /S
```

Full removal is explicit because progress is user data rather than cache. The
visible uninstaller offers the same choice as a checkbox; automation uses:

```powershell
"$env:LOCALAPPDATA\Programs\ikna\Uninstall.exe" /S /PURGE=1
```

`/PURGE=1` additionally removes `%APPDATA%\Ikna`, including the database,
settings, installed fonts and logs.

## Building

First create the Windows application image, then compile the checked-in script:

```powershell
gradle --no-daemon :desktop:createReleaseDistributable
./tools/nsis/build-installer.ps1
```

`build-installer.ps1` reads the numeric `packageVersion`, validates that the
image contains `Ikna.exe`, finds NSIS 3 and writes the installer under
`desktop/build/compose/binaries/main-release/nsis/`.

`tools/nsis/test-installer.ps1` performs two real Windows cycles in CI: `/S`
install plus preserving uninstall, then `/S` install plus `/PURGE=1` uninstall.
It verifies the application directory, shortcut, registry entry and user-data
behaviour after each cycle.

Development MSI builds made before this switch are no longer produced. If one
was installed manually, remove it once through Windows **Installed apps** before
using the NSIS setup; `%APPDATA%\Ikna` remains available to the new installation.
