; --------------------------------------------------------------------------
; Viglet Dumont DEP - NSIS Installer Script
;
; Requires NSIS 3.x
; Built by: scripts/package-dist.bat or GitHub Actions
; --------------------------------------------------------------------------

!include "MUI2.nsh"
!include "FileFunc.nsh"
!include "nsDialogs.nsh"
!include "LogicLib.nsh"

; --------------------------------------------------------------------------
; General
; --------------------------------------------------------------------------
Name "Viglet Dumont DEP"
!ifndef OUTDIR
  !define OUTDIR "..\..\target"
!endif
OutFile "${OUTDIR}\dumont-install.exe"
InstallDir "$PROGRAMFILES64\Viglet\Dumont"
InstallDirRegKey HKLM "Software\Viglet\Dumont" "InstallDir"
RequestExecutionLevel admin
SetCompressor /SOLID lzma

; --------------------------------------------------------------------------
; Version info
; --------------------------------------------------------------------------
VIProductVersion "1.0.0.0"
VIAddVersionKey "ProductName" "Viglet Dumont DEP"
VIAddVersionKey "CompanyName" "Viglet"
VIAddVersionKey "FileDescription" "Viglet Dumont DEP Installer"
VIAddVersionKey "LegalCopyright" "Apache License 2.0"

; --------------------------------------------------------------------------
; Variables
; --------------------------------------------------------------------------
Var JavaHome
Var JavaDialog
Var JavaDirText
Var JavaDirBrowse
Var JavaStatusLabel

; --------------------------------------------------------------------------
; MUI Settings
; --------------------------------------------------------------------------
!define MUI_ICON "dumont.ico"
!define MUI_UNICON "dumont.ico"
!define MUI_WELCOMEFINISHPAGE_BITMAP "wizard-sidebar.bmp"
!define MUI_UNWELCOMEFINISHPAGE_BITMAP "wizard-sidebar.bmp"
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_BITMAP "wizard-header.bmp"
!define MUI_HEADERIMAGE_RIGHT
!define MUI_ABORTWARNING

; --------------------------------------------------------------------------
; Pages
; --------------------------------------------------------------------------
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "..\..\LICENSE"
!insertmacro MUI_PAGE_DIRECTORY
Page custom JavaPageCreate JavaPageLeave
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "Portuguese"

; --------------------------------------------------------------------------
; Java Page - Auto-detect and allow user to browse
; --------------------------------------------------------------------------
Function JavaPageCreate
    !insertmacro MUI_HEADER_TEXT "Java Configuration" "Select the Java 21+ installation directory."

    nsDialogs::Create 1018
    Pop $JavaDialog
    ${If} $JavaDialog == error
        Abort
    ${EndIf}

    ; Try auto-detect: JAVA_HOME env, then registry, then PATH
    StrCpy $JavaHome ""

    ; 1. Check JAVA_HOME environment variable
    ReadEnvStr $0 "JAVA_HOME"
    ${If} $0 != ""
    ${AndIf} ${FileExists} "$0\bin\java.exe"
        StrCpy $JavaHome "$0"
    ${EndIf}

    ; 2. Check registry (Oracle JDK / Adoptium / Temurin)
    ${If} $JavaHome == ""
        ReadRegStr $0 HKLM "SOFTWARE\Eclipse Adoptium\JDK\21" "Path"
        ${If} $0 != ""
        ${AndIf} ${FileExists} "$0\bin\java.exe"
            StrCpy $JavaHome "$0"
        ${EndIf}
    ${EndIf}

    ${If} $JavaHome == ""
        ReadRegStr $0 HKLM "SOFTWARE\Eclipse Foundation\JDK\21" "Path"
        ${If} $0 != ""
        ${AndIf} ${FileExists} "$0\bin\java.exe"
            StrCpy $JavaHome "$0"
        ${EndIf}
    ${EndIf}

    ${If} $JavaHome == ""
        ReadRegStr $0 HKLM "SOFTWARE\JavaSoft\Java Development Kit\21" "JavaHome"
        ${If} $0 != ""
        ${AndIf} ${FileExists} "$0\bin\java.exe"
            StrCpy $JavaHome "$0"
        ${EndIf}
    ${EndIf}

    ; 3. Try common paths
    ${If} $JavaHome == ""
        ${If} ${FileExists} "$PROGRAMFILES64\Eclipse Adoptium\jdk-21*\bin\java.exe"
            StrCpy $JavaHome "$PROGRAMFILES64\Eclipse Adoptium\jdk-21"
        ${EndIf}
    ${EndIf}

    ; Description
    ${NSD_CreateLabel} 0 0 100% 36u \
        "Dumont DEP requires Java 21 or later. Select the Java installation directory (the folder containing the 'bin' subdirectory with java.exe)."
    Pop $0

    ; Label
    ${NSD_CreateLabel} 0 44u 100% 12u "Java Home directory:"
    Pop $0

    ; Text field
    ${NSD_CreateDirRequest} 0 58u 80% 12u "$JavaHome"
    Pop $JavaDirText

    ; Browse button
    ${NSD_CreateBrowseButton} 82% 57u 18% 14u "Browse..."
    Pop $JavaDirBrowse
    ${NSD_OnClick} $JavaDirBrowse JavaBrowse

    ; Status label
    ${NSD_CreateLabel} 0 78u 100% 12u ""
    Pop $JavaStatusLabel

    ; Show status for auto-detected path
    ${If} $JavaHome != ""
        ${NSD_SetText} $JavaStatusLabel "Auto-detected: $JavaHome"
    ${Else}
        ${NSD_SetText} $JavaStatusLabel "Java not detected. Please browse to your Java installation."
    ${EndIf}

    nsDialogs::Show
FunctionEnd

Function JavaBrowse
    nsDialogs::SelectFolderDialog "Select Java Home directory" "$JavaHome"
    Pop $0
    ${If} $0 != error
        StrCpy $JavaHome "$0"
        ${NSD_SetText} $JavaDirText "$JavaHome"
        ${If} ${FileExists} "$JavaHome\bin\java.exe"
            ${NSD_SetText} $JavaStatusLabel "Found: $JavaHome\bin\java.exe"
        ${Else}
            ${NSD_SetText} $JavaStatusLabel "Warning: java.exe not found in $JavaHome\bin\"
        ${EndIf}
    ${EndIf}
FunctionEnd

Function JavaPageLeave
    ${NSD_GetText} $JavaDirText $JavaHome

    ; Validate that java.exe exists
    ${IfNot} ${FileExists} "$JavaHome\bin\java.exe"
        MessageBox MB_YESNO|MB_ICONEXCLAMATION \
            "java.exe was not found in:$\n$JavaHome\bin\$\n$\nDumont DEP requires Java 21+. Continue anyway?" \
            IDYES +2
        Abort
    ${EndIf}
FunctionEnd

; --------------------------------------------------------------------------
; Installer Section
; --------------------------------------------------------------------------
Section "Dumont DEP" SecMain
    SectionIn RO

    SetOutPath "$INSTDIR"
    File "${STAGE}\README.txt"
    File "dumont.ico"

    ; Connector engine
    SetOutPath "$INSTDIR\connector"
    File "${STAGE}\connector\dumont-connector.jar"
    File "${STAGE}\connector\dumont-connector.properties"

    ; AEM plugin
    SetOutPath "$INSTDIR\connector\libs\aem"
    File "${STAGE}\connector\libs\aem\aem-plugin.jar"

    ; Web Crawler plugin
    SetOutPath "$INSTDIR\connector\libs\webcrawler"
    File "${STAGE}\connector\libs\webcrawler\web-crawler-plugin.jar"

    ; Database CLI
    SetOutPath "$INSTDIR\db"
    File "${STAGE}\db\dumont-db.jar"

    ; Filesystem CLI
    SetOutPath "$INSTDIR\filesystem"
    File "${STAGE}\filesystem\dumont-filesystem.jar"

    ; Bin scripts
    SetOutPath "$INSTDIR\bin"
    File "${STAGE}\bin\dumont-aem.sh"
    File "${STAGE}\bin\dumont-aem.bat"
    File "${STAGE}\bin\dumont-webcrawler.sh"
    File "${STAGE}\bin\dumont-webcrawler.bat"
    File "${STAGE}\bin\dumont-db.sh"
    File "${STAGE}\bin\dumont-db.bat"
    File "${STAGE}\bin\dumont-filesystem.sh"
    File "${STAGE}\bin\dumont-filesystem.bat"

    ; Write java-home.conf with the selected Java path
    FileOpen $0 "$INSTDIR\bin\java-home.conf" w
    FileWrite $0 "$JavaHome"
    FileClose $0

    ; Save Java path to registry for future updates
    WriteRegStr HKLM "Software\Viglet\Dumont" "JavaHome" "$JavaHome"

    ; Registry
    WriteRegStr HKLM "Software\Viglet\Dumont" "InstallDir" "$INSTDIR"

    ; Uninstaller
    WriteUninstaller "$INSTDIR\uninstall.exe"

    ; Add/Remove Programs entry
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\VigletDumont" \
        "DisplayName" "Viglet Dumont DEP"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\VigletDumont" \
        "UninstallString" '"$INSTDIR\uninstall.exe"'
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\VigletDumont" \
        "InstallLocation" "$INSTDIR"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\VigletDumont" \
        "Publisher" "Viglet"
    WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\VigletDumont" \
        "NoModify" 1
    WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\VigletDumont" \
        "NoRepair" 1

    ; Calculate installed size
    ${GetSize} "$INSTDIR" "/S=0K" $0 $1 $2
    IntFmt $0 "0x%08X" $0
    WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\VigletDumont" \
        "EstimatedSize" "$0"

    ; Add/Remove Programs icon
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\VigletDumont" \
        "DisplayIcon" '"$INSTDIR\dumont.ico"'

    ; Start Menu shortcuts
    CreateDirectory "$SMPROGRAMS\Viglet Dumont"
    CreateShortcut "$SMPROGRAMS\Viglet Dumont\Dumont AEM Connector.lnk" \
        "$INSTDIR\bin\dumont-aem.bat" "" "$INSTDIR\dumont.ico" "" SW_SHOWMINIMIZED
    CreateShortcut "$SMPROGRAMS\Viglet Dumont\Dumont Web Crawler.lnk" \
        "$INSTDIR\bin\dumont-webcrawler.bat" "" "$INSTDIR\dumont.ico" "" SW_SHOWMINIMIZED
    CreateShortcut "$SMPROGRAMS\Viglet Dumont\Uninstall.lnk" \
        "$INSTDIR\uninstall.exe"

SectionEnd

; --------------------------------------------------------------------------
; Uninstaller Section
; --------------------------------------------------------------------------
Section "Uninstall"
    ; Remove files
    RMDir /r "$INSTDIR\connector"
    RMDir /r "$INSTDIR\db"
    RMDir /r "$INSTDIR\filesystem"
    RMDir /r "$INSTDIR\bin"
    Delete "$INSTDIR\README.txt"
    Delete "$INSTDIR\dumont.ico"
    Delete "$INSTDIR\uninstall.exe"
    RMDir "$INSTDIR"

    ; Start Menu
    RMDir /r "$SMPROGRAMS\Viglet Dumont"

    ; Registry
    DeleteRegKey HKLM "Software\Viglet\Dumont"
    DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\VigletDumont"
SectionEnd
