; ikna Windows installer
; ======================
;
; This file is intentionally committed instead of being generated inside CI.
; It is the complete, reviewable description of what the Windows installer
; writes and removes. The Gradle build only creates the application image;
; tools/nsis/build-installer.ps1 passes that folder to this script.
;
; Installation model
; ------------------
; * Per-user: $LOCALAPPDATA\Programs\ikna, so normal installation needs no UAC.
; * /S performs a completely silent install. NSIS also supports /D=path as the
;   final command-line argument when a managed custom location is needed.
; * The application folder is replaced atomically enough for an in-place update:
;   it is removed only when our private marker file is present and ikna is closed.
; * The installer creates one Start-menu shortcut and one HKCU uninstall entry.
;   It creates no service, scheduled task, browser item or auto-start entry.
;
; Uninstallation model
; --------------------
; * Uninstall.exe /S removes the application, shortcut and registry entries.
; * Cards and settings in $APPDATA\Ikna are user data, not installer debris, and
;   are preserved by default. The visible uninstaller offers a clear checkbox.
; * Uninstall.exe /S /PURGE=1 also removes cards, progress, settings and logs.
; * A missing marker stops removal instead of recursively deleting an unknown
;   folder. This is why RMDir /r is safe here rather than merely convenient.

Unicode true
ManifestDPIAware true
RequestExecutionLevel user
CRCCheck on
SetCompressor /SOLID lzma
SetCompressorDictSize 64
SilentInstall normal
SilentUnInstall normal
AutoCloseWindow false

!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "FileFunc.nsh"
!include "nsDialogs.nsh"

!ifndef APP_VERSION
!define APP_VERSION "0.10.0"
!endif
!ifndef APP_VERSION_QUAD
!define APP_VERSION_QUAD "0.10.0.0"
!endif
!ifndef APP_IMAGE
!error "APP_IMAGE must point to the jpackage Ikna application image"
!endif
!ifndef OUTPUT_FILE
!define OUTPUT_FILE "ikna-${APP_VERSION}-windows-x64-setup.exe"
!endif

!define APP_NAME "ikna"
!define APP_EXE "Ikna.exe"
!define APP_REG_KEY "Software\ikna"
!define UNINSTALL_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\ikna"
!define MARKER_FILE ".ikna-install-root"
!define PROJECT_URL "https://github.com/d1d2dopamine/ikna"

Name "${APP_NAME}"
Caption "${APP_NAME} ${APP_VERSION}"
OutFile "${OUTPUT_FILE}"
InstallDir "$LOCALAPPDATA\Programs\ikna"
InstallDirRegKey HKCU "${APP_REG_KEY}" "InstallLocation"
BrandingText "ikna"
ShowInstDetails nevershow
ShowUninstDetails nevershow

VIProductVersion "${APP_VERSION_QUAD}"
VIAddVersionKey "ProductName" "ikna"
VIAddVersionKey "FileDescription" "ikna installer"
VIAddVersionKey "FileVersion" "${APP_VERSION}"
VIAddVersionKey "ProductVersion" "${APP_VERSION}"
VIAddVersionKey "CompanyName" "ikna"
VIAddVersionKey "LegalCopyright" "GPL-3.0-or-later"

; Modern UI keeps native controls, keyboard navigation, DPI behaviour and screen
; reader semantics. Only its bitmap, colours, spacing and words are branded.
!define MUI_ICON "${__FILEDIR__}\..\icon.ico"
!define MUI_UNICON "${__FILEDIR__}\..\icon.ico"
!define MUI_WELCOMEFINISHPAGE_BITMAP "${__FILEDIR__}\sidebar.bmp"
!define MUI_UNWELCOMEFINISHPAGE_BITMAP "${__FILEDIR__}\sidebar.bmp"
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_RIGHT
!define MUI_HEADERIMAGE_BITMAP "${__FILEDIR__}\header.bmp"
!define MUI_HEADERIMAGE_UNBITMAP "${__FILEDIR__}\header.bmp"
!define MUI_BGCOLOR "D0D3D9"
!define MUI_TEXTCOLOR "0E1526"
!define MUI_INSTFILESPAGE_COLORS "0E1526 D0D3D9"
!define MUI_ABORTWARNING
!define MUI_WELCOMEPAGE_TITLE_3LINES
!define MUI_WELCOMEPAGE_TITLE "$(WelcomeTitle)"
!define MUI_WELCOMEPAGE_TEXT "$(WelcomeText)"
!define MUI_FINISHPAGE_RUN "$INSTDIR\${APP_EXE}"
!define MUI_FINISHPAGE_RUN_TEXT "$(FinishRun)"
!define MUI_FINISHPAGE_LINK "$(ProjectLink)"
!define MUI_FINISHPAGE_LINK_LOCATION "${PROJECT_URL}"
!define MUI_FINISHPAGE_NOREBOOTSUPPORT

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
UninstPage custom un.DataPageCreate un.DataPageLeave
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH

!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "Russian"
!insertmacro MUI_LANGUAGE "German"
!insertmacro MUI_LANGUAGE "French"
!insertmacro MUI_LANGUAGE "Spanish"
!insertmacro MUI_LANGUAGE "PortugueseBR"
!insertmacro MUI_LANGUAGE "Polish"

LangString WelcomeTitle ${LANG_ENGLISH} "Install ikna"
LangString WelcomeText ${LANG_ENGLISH} "A quiet installer for ikna and its private Java runtime.$\r$\n$\r$\nNo bundled offers, background services or automatic startup."
LangString FinishRun ${LANG_ENGLISH} "Run ikna"
LangString ProjectLink ${LANG_ENGLISH} "Project and source code"
LangString AppRunning ${LANG_ENGLISH} "ikna is running. Close it before installing or removing the application."
LangString UnsafeDirectory ${LANG_ENGLISH} "This folder contains files not created by ikna. Choose an empty folder so the installer never deletes somebody else's files."
LangString CannotClean ${LANG_ENGLISH} "The previous ikna application folder could not be removed. Close the application and try again."
LangString BrokenInstall ${LANG_ENGLISH} "The ikna installation marker is missing. Removal stopped instead of deleting an unknown folder."
LangString UnDataTitle ${LANG_ENGLISH} "Data and progress"
LangString UnDataSubtitle ${LANG_ENGLISH} "Choose what should remain on this device"
LangString UnDataText ${LANG_ENGLISH} "Application files, shortcuts and Windows registration will be removed. Cards and settings in $APPDATA\Ikna are kept unless you choose otherwise."
LangString UnDataCheckbox ${LANG_ENGLISH} "Also delete cards, progress, settings and logs"

LangString WelcomeTitle ${LANG_RUSSIAN} "Установить ikna"
LangString WelcomeText ${LANG_RUSSIAN} "Спокойная установка ikna и её собственной Java-среды.$\r$\n$\r$\nБез дополнительных предложений, фоновых служб и автозапуска."
LangString FinishRun ${LANG_RUSSIAN} "Запустить ikna"
LangString ProjectLink ${LANG_RUSSIAN} "Проект и исходный код"
LangString AppRunning ${LANG_RUSSIAN} "ikna сейчас запущена. Закрой приложение перед установкой или удалением."
LangString UnsafeDirectory ${LANG_RUSSIAN} "В этой папке есть файлы, которые создала не ikna. Выбери пустую папку, чтобы установщик никогда не удалял чужие файлы."
LangString CannotClean ${LANG_RUSSIAN} "Не удалось очистить предыдущую папку приложения. Закрой ikna и попробуй снова."
LangString BrokenInstall ${LANG_RUSSIAN} "Маркер установки ikna отсутствует. Удаление остановлено, чтобы не удалить неизвестную папку."
LangString UnDataTitle ${LANG_RUSSIAN} "Данные и прогресс"
LangString UnDataSubtitle ${LANG_RUSSIAN} "Выбери, что должно остаться на устройстве"
LangString UnDataText ${LANG_RUSSIAN} "Файлы приложения, ярлыки и регистрация Windows будут удалены. Карточки и настройки в $APPDATA\Ikna сохранятся, если не выбрать обратное."
LangString UnDataCheckbox ${LANG_RUSSIAN} "Также удалить карточки, прогресс, настройки и логи"

LangString WelcomeTitle ${LANG_GERMAN} "ikna installieren"
LangString WelcomeText ${LANG_GERMAN} "Eine ruhige Installation von ikna und seiner eigenen Java-Laufzeit.$\r$\n$\r$\nKeine Zusatzangebote, Hintergrunddienste oder Autostarts."
LangString FinishRun ${LANG_GERMAN} "ikna starten"
LangString ProjectLink ${LANG_GERMAN} "Projekt und Quellcode"
LangString AppRunning ${LANG_GERMAN} "ikna läuft. Schließe die Anwendung vor der Installation oder Deinstallation."
LangString UnsafeDirectory ${LANG_GERMAN} "Dieser Ordner enthält Dateien, die nicht von ikna erstellt wurden. Wähle einen leeren Ordner, damit der Installer keine fremden Dateien löscht."
LangString CannotClean ${LANG_GERMAN} "Der vorherige ikna-Ordner konnte nicht entfernt werden. Schließe die Anwendung und versuche es erneut."
LangString BrokenInstall ${LANG_GERMAN} "Die ikna-Installationsmarkierung fehlt. Die Deinstallation wurde gestoppt, statt einen unbekannten Ordner zu löschen."
LangString UnDataTitle ${LANG_GERMAN} "Daten und Fortschritt"
LangString UnDataSubtitle ${LANG_GERMAN} "Wähle, was auf diesem Gerät bleiben soll"
LangString UnDataText ${LANG_GERMAN} "Anwendung, Verknüpfungen und Windows-Einträge werden entfernt. Karten und Einstellungen in $APPDATA\Ikna bleiben erhalten, wenn du nichts anderes auswählst."
LangString UnDataCheckbox ${LANG_GERMAN} "Auch Karten, Fortschritt, Einstellungen und Protokolle löschen"

LangString WelcomeTitle ${LANG_FRENCH} "Installer ikna"
LangString WelcomeText ${LANG_FRENCH} "Une installation calme d'ikna et de son environnement Java privé.$\r$\n$\r$\nAucune offre ajoutée, aucun service en arrière-plan et aucun démarrage automatique."
LangString FinishRun ${LANG_FRENCH} "Lancer ikna"
LangString ProjectLink ${LANG_FRENCH} "Projet et code source"
LangString AppRunning ${LANG_FRENCH} "ikna est en cours d'exécution. Ferme l'application avant de l'installer ou de la supprimer."
LangString UnsafeDirectory ${LANG_FRENCH} "Ce dossier contient des fichiers qui ne viennent pas d'ikna. Choisis un dossier vide afin que l'installateur ne supprime jamais d'autres fichiers."
LangString CannotClean ${LANG_FRENCH} "L'ancien dossier d'ikna n'a pas pu être supprimé. Ferme l'application et réessaie."
LangString BrokenInstall ${LANG_FRENCH} "Le marqueur d'installation d'ikna est absent. La suppression s'arrête plutôt que d'effacer un dossier inconnu."
LangString UnDataTitle ${LANG_FRENCH} "Données et progression"
LangString UnDataSubtitle ${LANG_FRENCH} "Choisis ce qui doit rester sur cet appareil"
LangString UnDataText ${LANG_FRENCH} "L'application, les raccourcis et l'enregistrement Windows seront supprimés. Les cartes et réglages dans $APPDATA\Ikna sont conservés sauf choix contraire."
LangString UnDataCheckbox ${LANG_FRENCH} "Supprimer aussi les cartes, la progression, les réglages et les journaux"

LangString WelcomeTitle ${LANG_SPANISH} "Instalar ikna"
LangString WelcomeText ${LANG_SPANISH} "Una instalación tranquila de ikna y su entorno Java privado.$\r$\n$\r$\nSin ofertas añadidas, servicios en segundo plano ni inicio automático."
LangString FinishRun ${LANG_SPANISH} "Abrir ikna"
LangString ProjectLink ${LANG_SPANISH} "Proyecto y código fuente"
LangString AppRunning ${LANG_SPANISH} "ikna está abierta. Cierra la aplicación antes de instalarla o desinstalarla."
LangString UnsafeDirectory ${LANG_SPANISH} "Esta carpeta contiene archivos que no creó ikna. Elige una carpeta vacía para que el instalador nunca borre archivos ajenos."
LangString CannotClean ${LANG_SPANISH} "No se pudo borrar la carpeta anterior de ikna. Cierra la aplicación e inténtalo de nuevo."
LangString BrokenInstall ${LANG_SPANISH} "Falta el marcador de instalación de ikna. La desinstalación se detuvo para no borrar una carpeta desconocida."
LangString UnDataTitle ${LANG_SPANISH} "Datos y progreso"
LangString UnDataSubtitle ${LANG_SPANISH} "Elige qué debe permanecer en este dispositivo"
LangString UnDataText ${LANG_SPANISH} "Se quitarán la aplicación, los accesos directos y el registro de Windows. Las tarjetas y los ajustes de $APPDATA\Ikna se conservarán salvo que elijas lo contrario."
LangString UnDataCheckbox ${LANG_SPANISH} "Borrar también tarjetas, progreso, ajustes y registros"

LangString WelcomeTitle ${LANG_PORTUGUESEBR} "Instalar o ikna"
LangString WelcomeText ${LANG_PORTUGUESEBR} "Uma instalação tranquila do ikna e do ambiente Java próprio.$\r$\n$\r$\nSem ofertas adicionais, serviços em segundo plano ou início automático."
LangString FinishRun ${LANG_PORTUGUESEBR} "Abrir o ikna"
LangString ProjectLink ${LANG_PORTUGUESEBR} "Projeto e código-fonte"
LangString AppRunning ${LANG_PORTUGUESEBR} "O ikna está aberto. Feche o aplicativo antes de instalar ou remover."
LangString UnsafeDirectory ${LANG_PORTUGUESEBR} "Esta pasta contém arquivos que não foram criados pelo ikna. Escolha uma pasta vazia para que o instalador nunca apague arquivos de terceiros."
LangString CannotClean ${LANG_PORTUGUESEBR} "Não foi possível remover a pasta anterior do ikna. Feche o aplicativo e tente novamente."
LangString BrokenInstall ${LANG_PORTUGUESEBR} "O marcador de instalação do ikna está ausente. A remoção foi interrompida para não apagar uma pasta desconhecida."
LangString UnDataTitle ${LANG_PORTUGUESEBR} "Dados e progresso"
LangString UnDataSubtitle ${LANG_PORTUGUESEBR} "Escolha o que deve permanecer neste dispositivo"
LangString UnDataText ${LANG_PORTUGUESEBR} "O aplicativo, os atalhos e o registro do Windows serão removidos. Os cartões e ajustes em $APPDATA\Ikna serão mantidos, a menos que você escolha o contrário."
LangString UnDataCheckbox ${LANG_PORTUGUESEBR} "Apagar também cartões, progresso, ajustes e registros"

LangString WelcomeTitle ${LANG_POLISH} "Zainstaluj ikna"
LangString WelcomeText ${LANG_POLISH} "Spokojna instalacja ikna i własnego środowiska Java.$\r$\n$\r$\nBez dodatkowych ofert, usług w tle i autostartu."
LangString FinishRun ${LANG_POLISH} "Uruchom ikna"
LangString ProjectLink ${LANG_POLISH} "Projekt i kod źródłowy"
LangString AppRunning ${LANG_POLISH} "ikna jest uruchomiona. Zamknij aplikację przed instalacją lub usunięciem."
LangString UnsafeDirectory ${LANG_POLISH} "Ten folder zawiera pliki, których nie utworzyła ikna. Wybierz pusty folder, aby instalator nigdy nie usunął cudzych plików."
LangString CannotClean ${LANG_POLISH} "Nie udało się usunąć poprzedniego folderu ikna. Zamknij aplikację i spróbuj ponownie."
LangString BrokenInstall ${LANG_POLISH} "Brakuje znacznika instalacji ikna. Usuwanie zatrzymano, aby nie skasować nieznanego folderu."
LangString UnDataTitle ${LANG_POLISH} "Dane i postęp"
LangString UnDataSubtitle ${LANG_POLISH} "Wybierz, co ma pozostać na tym urządzeniu"
LangString UnDataText ${LANG_POLISH} "Aplikacja, skróty i wpisy Windows zostaną usunięte. Karty i ustawienia w $APPDATA\Ikna pozostaną, chyba że wybierzesz inaczej."
LangString UnDataCheckbox ${LANG_POLISH} "Usuń także karty, postęp, ustawienia i dzienniki"

Var AppLocked
Var PurgeData
Var PurgeCheckbox

Function CheckAppLocked
    StrCpy $AppLocked 0
    IfFileExists "$INSTDIR\${APP_EXE}" 0 check_app_done
    ClearErrors
    FileOpen $0 "$INSTDIR\${APP_EXE}" a
    IfErrors check_app_locked check_app_opened
check_app_opened:
    FileClose $0
    Goto check_app_done
check_app_locked:
    StrCpy $AppLocked 1
check_app_done:
FunctionEnd

Function StopForRunningApp
    IfSilent running_app_silent running_app_visible
running_app_visible:
    MessageBox MB_OK|MB_ICONEXCLAMATION "$(AppRunning)" /SD IDOK
running_app_silent:
    SetErrorLevel 32
    Quit
FunctionEnd

Function .onInit
    SetShellVarContext current
    ReadRegStr $0 HKCU "${APP_REG_KEY}" "InstallLocation"
    StrCmp $0 "" init_use_default
    StrCpy $INSTDIR $0
init_use_default:
    Call CheckAppLocked
    StrCmp $AppLocked 0 init_done
    Call StopForRunningApp
init_done:
FunctionEnd

; Only a directory carrying our marker may be recursively replaced. An empty
; custom directory is fine. A non-empty unmarked directory is never touched.
Function PrepareInstallDirectory
    IfFileExists "$INSTDIR\${MARKER_FILE}" prepare_managed
    IfFileExists "$INSTDIR\*.*" prepare_occupied prepare_fresh

prepare_managed:
    Call CheckAppLocked
    StrCmp $AppLocked 0 prepare_remove
    Call StopForRunningApp
prepare_remove:
    ClearErrors
    RMDir /r "$INSTDIR"
    IfErrors prepare_remove_failed prepare_fresh

prepare_remove_failed:
    IfSilent prepare_remove_silent prepare_remove_visible
prepare_remove_visible:
    MessageBox MB_OK|MB_ICONSTOP "$(CannotClean)" /SD IDOK
prepare_remove_silent:
    SetErrorLevel 5
    Quit

prepare_occupied:
    IfSilent prepare_occupied_silent prepare_occupied_visible
prepare_occupied_visible:
    MessageBox MB_OK|MB_ICONSTOP "$(UnsafeDirectory)" /SD IDOK
prepare_occupied_silent:
    SetErrorLevel 13
    Quit

prepare_fresh:
    CreateDirectory "$INSTDIR"
FunctionEnd

Section "ikna" SEC_IKNA
    SetShellVarContext current
    Call PrepareInstallDirectory
    SetOutPath "$INSTDIR"

    ; The jpackage image already contains Ikna.exe, jars, native libraries and a
    ; reduced Java runtime. Copy the complete image; splitting it breaks relative
    ; launcher paths and creates an installation that only works on the CI host.
    File /r "${APP_IMAGE}\*.*"
    File /oname=LICENSE.txt "${__FILEDIR__}\..\..\LICENSE"

    FileOpen $0 "$INSTDIR\${MARKER_FILE}" w
    FileWrite $0 "ikna-nsis-install-root ${APP_VERSION}$\r$\n"
    FileClose $0
    WriteUninstaller "$INSTDIR\Uninstall.exe"

    CreateDirectory "$SMPROGRAMS\ikna"
    CreateShortcut "$SMPROGRAMS\ikna\ikna.lnk" "$INSTDIR\${APP_EXE}" "" "$INSTDIR\${APP_EXE}" 0

    WriteRegStr HKCU "${APP_REG_KEY}" "InstallLocation" "$INSTDIR"
    WriteRegStr HKCU "${APP_REG_KEY}" "Version" "${APP_VERSION}"
    WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayName" "ikna"
    WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayVersion" "${APP_VERSION}"
    WriteRegStr HKCU "${UNINSTALL_KEY}" "Publisher" "ikna"
    WriteRegStr HKCU "${UNINSTALL_KEY}" "InstallLocation" "$INSTDIR"
    WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayIcon" "$INSTDIR\${APP_EXE},0"
    WriteRegStr HKCU "${UNINSTALL_KEY}" "URLInfoAbout" "${PROJECT_URL}"
    WriteRegStr HKCU "${UNINSTALL_KEY}" "UninstallString" "$\"$INSTDIR\Uninstall.exe$\""
    WriteRegStr HKCU "${UNINSTALL_KEY}" "QuietUninstallString" "$\"$INSTDIR\Uninstall.exe$\" /S"
    WriteRegDWORD HKCU "${UNINSTALL_KEY}" "NoModify" 1
    WriteRegDWORD HKCU "${UNINSTALL_KEY}" "NoRepair" 1
    ${GetSize} "$INSTDIR" "/S=0K" $0 $1 $2
    WriteRegDWORD HKCU "${UNINSTALL_KEY}" "EstimatedSize" $0
    SetErrorLevel 0
SectionEnd

Function un.CheckAppLocked
    StrCpy $AppLocked 0
    IfFileExists "$INSTDIR\${APP_EXE}" 0 un_check_done
    ClearErrors
    FileOpen $0 "$INSTDIR\${APP_EXE}" a
    IfErrors un_check_locked un_check_opened
un_check_opened:
    FileClose $0
    Goto un_check_done
un_check_locked:
    StrCpy $AppLocked 1
un_check_done:
FunctionEnd

Function un.StopForRunningApp
    IfSilent un_running_silent un_running_visible
un_running_visible:
    MessageBox MB_OK|MB_ICONEXCLAMATION "$(AppRunning)" /SD IDOK
un_running_silent:
    SetErrorLevel 32
    Quit
FunctionEnd

Function un.onInit
    SetShellVarContext current
    StrCpy $PurgeData 0

    ; The uninstaller may recursively remove only the directory containing the
    ; marker written by the install section above.
    IfFileExists "$INSTDIR\${MARKER_FILE}" un_marker_ok
    IfSilent un_marker_silent un_marker_visible
un_marker_visible:
    MessageBox MB_OK|MB_ICONSTOP "$(BrokenInstall)" /SD IDOK
un_marker_silent:
    SetErrorLevel 13
    Abort

un_marker_ok:
    ${GetParameters} $0
    ClearErrors
    ${GetOptions} $0 "/PURGE=" $1
    IfErrors un_purge_parsed
    StrCmp $1 "1" 0 un_purge_parsed
    StrCpy $PurgeData 1
un_purge_parsed:
    Call un.CheckAppLocked
    StrCmp $AppLocked 0 un_init_done
    Call un.StopForRunningApp
un_init_done:
FunctionEnd

Function un.DataPageCreate
    !insertmacro MUI_HEADER_TEXT "$(UnDataTitle)" "$(UnDataSubtitle)"
    nsDialogs::Create 1018
    Pop $0
    StrCmp $0 error un_data_abort

    ${NSD_CreateLabel} 0 0 100% 38u "$(UnDataText)"
    Pop $0
    ${NSD_CreateCheckbox} 0 48u 100% 24u "$(UnDataCheckbox)"
    Pop $PurgeCheckbox
    StrCmp $PurgeData 1 0 un_data_show
    ${NSD_Check} $PurgeCheckbox
un_data_show:
    nsDialogs::Show
    Return
un_data_abort:
    Abort
FunctionEnd

Function un.DataPageLeave
    ${NSD_GetState} $PurgeCheckbox $0
    StrCmp $0 ${BST_CHECKED} 0 un_data_keep
    StrCpy $PurgeData 1
    Return
un_data_keep:
    StrCpy $PurgeData 0
FunctionEnd

Section "Uninstall"
    SetShellVarContext current

    Delete "$SMPROGRAMS\ikna\ikna.lnk"
    RMDir "$SMPROGRAMS\ikna"
    ; Remove names created by old development installers too. No unrelated Start
    ; menu folder is removed because RMDir succeeds only when it is empty.
    Delete "$SMPROGRAMS\Ikna\Ikna.lnk"
    RMDir "$SMPROGRAMS\Ikna"
    Delete "$DESKTOP\ikna.lnk"
    Delete "$DESKTOP\Ikna.lnk"

    DeleteRegKey HKCU "${UNINSTALL_KEY}"
    DeleteRegKey HKCU "${APP_REG_KEY}"

    StrCmp $PurgeData 1 0 un_keep_data
    RMDir /r "$APPDATA\Ikna"
un_keep_data:

    ; The marker was checked in un.onInit before this destructive operation.
    RMDir /r "$INSTDIR"
    SetErrorLevel 0
SectionEnd
