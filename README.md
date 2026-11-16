# Attendance Management System

A Java Swing application for managing students, recording attendance, and viewing attendance reports. Attendance data is stored locally in an SQLite database.

## Requirements

- Java 8 or later

## Run on Windows

Compile the application:

```powershell
javac -cp "sqlite-jdbc-3.54.1.0.jar" AttendanceManagementSystem.java
```

Run it:

```powershell
java -cp ".;sqlite-jdbc-3.54.1.0.jar" AttendanceManagementSystem
```

The application creates `attendance.db` in the current working directory when it starts. This local database is intentionally excluded from version control.
