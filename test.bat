  @echo off
  setlocal EnableExtensions

  call "%~dp0android-common.bat" init "%~dp0"
  call "%~dp0android-common.bat" ensure_adb "%~dp0"
  if errorlevel 1 (
    echo [ERROR] Khong tim thay adb.exe.
    echo Them Android SDK Platform-Tools vao PATH hoac kiem tra local.properties.
    exit /b 1
  )

  echo.
  echo ===== BUILD + INSTALL DEBUG =====
  pushd "%PROJECT_DIR%" >nul
  call gradlew.bat installDebug
  set "EXIT_CODE=%ERRORLEVEL%"
  popd >nul

  if not "%EXIT_CODE%"=="0" (
    echo [ERROR] Build hoac cai app that bai.
    exit /b %EXIT_CODE%
  )

  echo.
  echo Da build va cai ban debug.
  exit /b 0
