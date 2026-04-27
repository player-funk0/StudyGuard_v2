@rem Gradle wrapper script for Windows
@rem https://gradle.org/docs/current/userguide/gradle_wrapper.html

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem Validate JVM
@rem ##########################################################################
if not "%JAVA_HOME%"=="" (
  set JAVACMD=%JAVA_HOME%\bin\java.exe
) else (
  set JAVACMD=java.exe
)

@rem ##########################################################################
@rem Resolve application home
@rem ##########################################################################
set APP_HOME=%~dp0

@rem ##########################################################################
@rem Execute Gradle wrapper
@rem ##########################################################################
"%JAVACMD%" -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
