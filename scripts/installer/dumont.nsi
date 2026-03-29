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
VIProductVersion "2026.2.1.0"
VIAddVersionKey "ProductName" "Viglet Dumont DEP"
VIAddVersionKey "FileVersion" "2026.2.1.0"
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

; Indexing provider variables
Var ProviderDialog
Var RadioTuring
Var RadioElasticsearch
Var RadioSolr
Var IndexingProvider        ; "turing", "elasticsearch" or "solr"

; Provider detail page
Var ProviderDetailDialog

; Turing fields
Var TuringUrl
Var TuringApiKey
Var TuringUrlText
Var TuringApiKeyText

; Elasticsearch fields
Var ElasticsearchUrl
Var ElasticsearchIndex
Var ElasticsearchUrlText
Var ElasticsearchIndexText

; Solr fields
Var SolrUrl
Var SolrCore
Var SolrUrlText
Var SolrCoreText

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
Page custom ProviderPageCreate ProviderPageLeave
Page custom ProviderDetailPageCreate ProviderDetailPageLeave
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
; Indexing Provider Page - Choose between Turing, Elasticsearch, Solr
; --------------------------------------------------------------------------
Function ProviderPageCreate
    !insertmacro MUI_HEADER_TEXT "Indexing Provider" "Select the search engine to use for indexing."

    nsDialogs::Create 1018
    Pop $ProviderDialog
    ${If} $ProviderDialog == error
        Abort
    ${EndIf}

    ; Default to turing if not set
    ${If} $IndexingProvider == ""
        StrCpy $IndexingProvider "turing"
    ${EndIf}

    ${NSD_CreateLabel} 0 0 100% 24u \
        "Choose which search engine Dumont will use to index content:"
    Pop $0

    ${NSD_CreateRadioButton} 10u 32u 100% 12u "Turing"
    Pop $RadioTuring

    ${NSD_CreateLabel} 24u 46u 100% 12u "Viglet Turing AI platform (recommended)"
    Pop $0

    ${NSD_CreateRadioButton} 10u 66u 100% 12u "Elasticsearch"
    Pop $RadioElasticsearch

    ${NSD_CreateLabel} 24u 80u 100% 12u "Connect directly to an Elasticsearch cluster"
    Pop $0

    ${NSD_CreateRadioButton} 10u 100u 100% 12u "Apache Solr"
    Pop $RadioSolr

    ${NSD_CreateLabel} 24u 114u 100% 12u "Connect directly to an Apache Solr instance"
    Pop $0

    ; Restore previous selection
    ${If} $IndexingProvider == "elasticsearch"
        ${NSD_Check} $RadioElasticsearch
    ${ElseIf} $IndexingProvider == "solr"
        ${NSD_Check} $RadioSolr
    ${Else}
        ${NSD_Check} $RadioTuring
    ${EndIf}

    nsDialogs::Show
FunctionEnd

Function ProviderPageLeave
    ${NSD_GetState} $RadioTuring $0
    ${If} $0 == ${BST_CHECKED}
        StrCpy $IndexingProvider "turing"
    ${EndIf}

    ${NSD_GetState} $RadioElasticsearch $0
    ${If} $0 == ${BST_CHECKED}
        StrCpy $IndexingProvider "elasticsearch"
    ${EndIf}

    ${NSD_GetState} $RadioSolr $0
    ${If} $0 == ${BST_CHECKED}
        StrCpy $IndexingProvider "solr"
    ${EndIf}
FunctionEnd

; --------------------------------------------------------------------------
; Provider Detail Page - Collect connection details for chosen provider
; --------------------------------------------------------------------------
Function ProviderDetailPageCreate
    ${If} $IndexingProvider == "turing"
        !insertmacro MUI_HEADER_TEXT "Turing Configuration" "Enter the Turing server connection details."
    ${ElseIf} $IndexingProvider == "elasticsearch"
        !insertmacro MUI_HEADER_TEXT "Elasticsearch Configuration" "Enter the Elasticsearch connection details."
    ${Else}
        !insertmacro MUI_HEADER_TEXT "Apache Solr Configuration" "Enter the Solr connection details."
    ${EndIf}

    nsDialogs::Create 1018
    Pop $ProviderDetailDialog
    ${If} $ProviderDetailDialog == error
        Abort
    ${EndIf}

    ; --- Turing fields ---
    ${If} $IndexingProvider == "turing"
        ; Default values
        ${If} $TuringUrl == ""
            StrCpy $TuringUrl "http://localhost:2700"
        ${EndIf}

        ${NSD_CreateLabel} 0 0 100% 12u "Turing URL:"
        Pop $0

        ${NSD_CreateText} 0 14u 100% 12u "$TuringUrl"
        Pop $TuringUrlText

        ${NSD_CreateLabel} 0 36u 100% 12u "API Key:"
        Pop $0

        ${NSD_CreateText} 0 50u 100% 12u "$TuringApiKey"
        Pop $TuringApiKeyText
    ${EndIf}

    ; --- Elasticsearch fields ---
    ${If} $IndexingProvider == "elasticsearch"
        ${If} $ElasticsearchUrl == ""
            StrCpy $ElasticsearchUrl "http://localhost:9200"
        ${EndIf}
        ${If} $ElasticsearchIndex == ""
            StrCpy $ElasticsearchIndex "dumont"
        ${EndIf}

        ${NSD_CreateLabel} 0 0 100% 12u "Elasticsearch URL:"
        Pop $0

        ${NSD_CreateText} 0 14u 100% 12u "$ElasticsearchUrl"
        Pop $ElasticsearchUrlText

        ${NSD_CreateLabel} 0 36u 100% 12u "Index name:"
        Pop $0

        ${NSD_CreateText} 0 50u 100% 12u "$ElasticsearchIndex"
        Pop $ElasticsearchIndexText
    ${EndIf}

    ; --- Solr fields ---
    ${If} $IndexingProvider == "solr"
        ${If} $SolrUrl == ""
            StrCpy $SolrUrl "http://localhost:8983/solr"
        ${EndIf}
        ${If} $SolrCore == ""
            StrCpy $SolrCore "dumont"
        ${EndIf}

        ${NSD_CreateLabel} 0 0 100% 12u "Solr URL:"
        Pop $0

        ${NSD_CreateText} 0 14u 100% 12u "$SolrUrl"
        Pop $SolrUrlText

        ${NSD_CreateLabel} 0 36u 100% 12u "Core name:"
        Pop $0

        ${NSD_CreateText} 0 50u 100% 12u "$SolrCore"
        Pop $SolrCoreText
    ${EndIf}

    nsDialogs::Show
FunctionEnd

Function ProviderDetailPageLeave
    ${If} $IndexingProvider == "turing"
        ${NSD_GetText} $TuringUrlText $TuringUrl
        ${NSD_GetText} $TuringApiKeyText $TuringApiKey
    ${ElseIf} $IndexingProvider == "elasticsearch"
        ${NSD_GetText} $ElasticsearchUrlText $ElasticsearchUrl
        ${NSD_GetText} $ElasticsearchIndexText $ElasticsearchIndex
    ${Else}
        ${NSD_GetText} $SolrUrlText $SolrUrl
        ${NSD_GetText} $SolrCoreText $SolrCore
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

    ; Write dumont-connector.properties with user-selected indexing provider
    FileOpen $0 "$INSTDIR\connector\dumont-connector.properties" w
    FileWrite $0 "# Dumont Connector - Configuration$\r$\n"
    FileWrite $0 "dumont.aem.querybuilder=true$\r$\n"
    FileWrite $0 "dumont.aem.querybuilder.parallelism=10$\r$\n"
    FileWrite $0 "$\r$\n"
    FileWrite $0 "# Indexing provider (options: turing, elasticsearch, solr)$\r$\n"
    FileWrite $0 "dumont.indexing.provider=$IndexingProvider$\r$\n"
    FileWrite $0 "$\r$\n"

    ${If} $IndexingProvider == "turing"
        FileWrite $0 "# Turing connection$\r$\n"
        FileWrite $0 "turing.url=$TuringUrl$\r$\n"
        FileWrite $0 "turing.apiKey=$TuringApiKey$\r$\n"
    ${ElseIf} $IndexingProvider == "elasticsearch"
        FileWrite $0 "# Elasticsearch connection$\r$\n"
        FileWrite $0 "dumont.indexing.elasticsearch.url=$ElasticsearchUrl$\r$\n"
        FileWrite $0 "dumont.indexing.elasticsearch.index=$ElasticsearchIndex$\r$\n"
    ${Else}
        FileWrite $0 "# Solr connection$\r$\n"
        FileWrite $0 "dumont.indexing.solr.url=$SolrUrl$\r$\n"
        FileWrite $0 "dumont.indexing.solr.core=$SolrCore$\r$\n"
    ${EndIf}

    FileWrite $0 "$\r$\n"
    FileWrite $0 "# Server port (use different ports for multiple connector instances)$\r$\n"
    FileWrite $0 "server.port=30130$\r$\n"
    FileClose $0

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
