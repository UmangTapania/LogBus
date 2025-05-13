# LogBus

LogBus is a powerful and flexible logging library for Android applications that provides enhanced logging capabilities with beautiful formatting and multiple output destinations.

## Features

- 🎨 Beautiful log formatting with customizable box styles
- 🎯 Multiple logging destinations (Logcat, File, Crashlytics)
- 🎭 Emoji support for better visual distinction of log levels
- 📦 Text wrapping and box borders for better readability
- 🚨 Automatic crash detection and reporting
- 🔄 Asynchronous logging operations
- 🎯 Customizable logging styles and configurations

## Installation

Add the following to your project's `build.gradle` file:

```gradle
dependencies {
    implementation 'com.github.umangtapania:logbus:1.0.0'
}
```

## Basic Usage

### Initialize LogBus

```kotlin
// Initialize with default configuration
LogBus.initialize(context)

// Or with custom configuration
val config = LogStyleConfig(
    box = LogBoxStyle.DOUBLE_BORDER,
    useEmoji = true,
    wrapText = true
)
LogBus.initialize(context, config)
```

### Logging Messages

```kotlin
// Basic logging
LogBus.d("TAG", "Debug message")
LogBus.i("TAG", "Info message")
LogBus.w("TAG", "Warning message")
LogBus.e("TAG", "Error message")
LogBus.v("TAG", "Verbose message")

// Logging with exception
try {
    // Your code
} catch (e: Exception) {
    LogBus.e("TAG", "Error occurred", e)
}
```

### Output Examples

#### Logcat Output
```
╔════════════════════════════════════════════════════════════════════════════╗
║  🔍 Debug message                                                          ║
╚════════════════════════════════════════════════════════════════════════════╝

╔════════════════════════════════════════════════════════════════════════════╗
║  ℹ️ Info message                                                           ║
╚════════════════════════════════════════════════════════════════════════════╝

╔════════════════════════════════════════════════════════════════════════════╗
║  ⚠️ Warning message                                                        ║
╚════════════════════════════════════════════════════════════════════════════╝

╔════════════════════════════════════════════════════════════════════════════╗
║  ❌ Error message                                                          ║
╚════════════════════════════════════════════════════════════════════════════╝
```

## Customization

### Box Styles

LogBus supports three different box styles:

```kotlin
// Single border style
LogBoxStyle.SINGLE_BORDER  // Uses: ┌─┐│└┘

// Double border style
LogBoxStyle.DOUBLE_BORDER  // Uses: ╔═╗║╚╝

// No border
LogBoxStyle.NONE
```

### Custom Style Configuration

```kotlin
val config = LogStyleConfig(
    box = LogBoxStyle.DOUBLE_BORDER,  // Choose box style
    useEmoji = true,                  // Enable/disable emojis
    wrapText = true                   // Enable/disable text wrapping
)
LogBus.initialize(context, config)
```

## Logging Routes

LogBus supports multiple logging destinations:

### Logcat Route
```kotlin
// Default route, logs to Android's Logcat
LogBus.addRoute(LogcatRoute())
```

### File Route
```kotlin
// Logs to a file in the application's storage
LogBus.addRoute(FileRoute(context))
```

### Crashlytics Route
```kotlin
// Logs errors and crashes
LogBus.addRoute(CrashlyticsRoute(context))
```

## Crash Reporting

LogBus automatically captures and reports crashes with detailed information:

```
🚨 CRASH DETECTED 🚨
Thread: main
Exception: java.lang.NullPointerException
Message: Attempt to invoke virtual method on a null object reference
Stacktrace: [Detailed stack trace]
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Author

Created by [Umang Tapania](https://github.com/umangtapania) 