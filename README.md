# parley-core

The core of **Parley**, a family of Java libraries for implementing internet protocols: small,
dependency-free building blocks that the other Parley projects share. It gives you property lookup and logging for any class, threads
that can be stopped cleanly, a TCP/SSL server and client base, and a few utilities.
The Swing date and time pickers that used to be here are now a separate library,
[swing-widgets](https://github.com/parley-project/swing-widgets) (`us.bringardner:bringardner-swing-widgets`).

- **Java 11** or later (virtual threads are used on Java 21+ when you ask for them)
- **No runtime dependencies.** log4j 2 is used when it is on the class path, but it is never required.
- Apache License 2.0

## Contents

- [Getting started](#getting-started)
- [What's inside](#whats-inside)
- [Properties](#properties)
- [Logging](#logging)
- [Threads and servers](#threads-and-servers)
- [SSL](#ssl)
- [Utilities](#utilities)
- [Building and testing](#building-and-testing)
- [Releasing](#releasing)
- [License](#license)

## Getting started

parley-core is on Maven Central, so no extra repository is needed:

```xml
<dependency>
    <groupId>us.bringardner.parley</groupId>
    <artifactId>parley-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

> parley-core was previously `us.bringardner:bjl_core` (BjlCore), with packages under
> `us.bringardner.core`. Moving over means changing the dependency and replacing
> `us.bringardner.core` with `us.bringardner.parley.core` in imports and in property names
> such as `us.bringardner.parley.core.ParleyLogger.LogLevel`. `BjlLogger` is now `ParleyLogger`.
> See [CHANGELOG.md](CHANGELOG.md) for everything that changed.

A class gets properties and logging by extending `BaseObject`:

```java
public class Mailer extends BaseObject {

    public void send(String to) {
        String host = getProperty("SmtpHost", "localhost");
        int port = getIntProperty("SmtpPort", 25);
        logDebug(() -> "Sending to " + to + " via " + host + ":" + port);
        ...
    }
}
```

## What's inside

| Package | Class | Purpose |
|---|---|---|
| `us.bringardner.parley.core` | `BaseObject` | Property lookup and logging for any class. |
| | `SecureBaseObject` | Adds key store, trust manager and `SSLContext` handling. |
| | `BaseThread` | A `Runnable` that can be started, stopped and restarted. |
| | `NamedThreadFactory` | A `ThreadFactory` for executors: named daemon (or virtual) threads. |
| | `ILogger` | The logging interface used everywhere in Parley. |
| | `ParleyLogger`, `Log4JLogger`, `JulLogger` | `ILogger` implementations: built in, log4j 2, `java.util.logging`. |
| `us.bringardner.parley.core.util` | `AbstractCoreServer` | Base class for a TCP or SSL server. |
| | `SocketClient` | Creates configured plain or SSL client sockets. |
| | `SocketOptions` | The timeout, linger, keep-alive and no-delay settings shared by servers and clients. |
| | `TlsSockets` | STARTTLS (TLS on a connected socket), SNI and host name checks for `SSLSocket` and `SSLEngine`. |
| | `TrustAllCertificates` | A trust manager that accepts every certificate, for tests and opportunistic TLS. |
| | `AddressMatcher` | A list of IP addresses and CIDR networks, for allow lists. Never does a DNS lookup. |
| | `PrivateKeys`, `Pem`, `Der` | Load RSA and EC private keys from PEM files and key stores. |
| | `Hex` | Hex encoding and decoding. |
| | `Base32Hex` | Base32 with the extended hex alphabet (RFC 4648), as DNSSEC NSEC3 uses it. |
| | `Ec` | Elliptic curve helpers: point encoding and checks, and ECDSA signatures between DER and raw (r, s) / r‖s form. |
| | `RenamedProperty` | Reads a system property under its new name, falling back to an old name (with a one-time deprecation warning). |
| | `LogHelper` | Logging for code that can't extend `BaseObject`. |

## Properties

`getProperty(name)` lets a class's author ship default values in the deployed code, lets a sub class
change them, and lets whoever runs the program change them again without rebuilding anything.
The layers, from weakest to strongest:

1. **The default** passed in the call, `getProperty(name, default)`. `getProperty(name)` returns `null`.
2. **A properties file** next to the class: `com/example/Mailer.properties` for `com.example.Mailer`.
   A sub class's file wins over its super class's file.
3. **A system property**, which wins over every file.

### Search order

`getProperty(name)` returns the first value it finds, looking in this order:

1. System property `<prefix>.<name>`. The prefix is the class name of the object (see
   [Changing the prefix](#changing-the-prefix)). Then `<super class name>.<name>` for each super class,
   up to (not including) `BaseObject`.
2. System property `<name>`.
3. For the object's class, then each super class up to (not including) `BaseObject`, the properties file
   `/<package path>/<ClassName>.properties`, checking `<prefix>.<name>`, `<that class name>.<name>`
   and then `<name>`. Inner and anonymous classes use their outer class's file.
4. The default value, or `null`.

Values are only the text found (`getProperty` does not trim or interpret it). An empty value
(`SmtpHost=`) is a value, so it stops the search.

### Example

```java
package com.example;

public class Mailer extends BaseObject {

    public void send(String to) {
        String host = getProperty("SmtpHost", "localhost");
        int port    = getIntProperty("SmtpPort", 25);
        boolean tls = getBooleanProperty("UseTls", false);
        ...
    }
}

public class BulkMailer extends Mailer {
}
```

`com/example/Mailer.properties`, in the jar, the defaults of the class's author:

```properties
SmtpHost = mail.example.com
SmtpPort = 587
UseTls   = true
```

`com/example/BulkMailer.properties`, in the jar or in the application that extends it:

```properties
SmtpHost = bulk.example.com
```

What each object sees:

| Call | Setup | Result |
|---|---|---|
| `new Mailer().getProperty("SmtpHost")` | nothing else | `mail.example.com` (Mailer.properties) |
| `new BulkMailer().getProperty("SmtpHost")` | nothing else | `bulk.example.com` (BulkMailer.properties wins over Mailer.properties) |
| `new BulkMailer().getIntProperty("SmtpPort", 25)` | nothing else | `587` (BulkMailer has no file entry, Mailer.properties is next) |
| `new Mailer().getProperty("Retries", "3")` | not defined anywhere | `3` (the default) |
| `new Mailer().getProperty("SmtpHost")` | `-Dcom.example.Mailer.SmtpHost=relay` | `relay` (a system property beats the file) |
| `new BulkMailer().getProperty("SmtpHost")` | `-Dcom.example.Mailer.SmtpHost=relay` | `relay` (a property for a super class also applies to its sub classes) |
| `new BulkMailer().getProperty("SmtpHost")` | the line above, and `-Dcom.example.BulkMailer.SmtpHost=bulk-relay` | `bulk-relay` (the object's own class name is looked up first) |
| any `BaseObject` | `-DSmtpHost=other` | `other` for every class that asks for `SmtpHost` |

A properties file may also use the class name form, `com.example.Mailer.SmtpHost = ...`. It is looked up
before the plain `SmtpHost`, so a file can hold a general value and a more specific one for its class.

### Which form to use for a system property

- `-Dcom.example.Mailer.SmtpHost=...` sets it for one class and its sub classes. Use this one.
- `-DSmtpHost=...` sets it for **every** `BaseObject` that asks for that name, and it also beats a
  class-specific value in a properties file. Use it for settings that really are global, and pick names
  that won't collide with JVM properties such as `user.dir`.

System properties are read on every call, so they can be changed while the program runs. Properties
files are cached (see [Caching](#caching)).

### Typed values

- `getIntProperty(name, default)` trims and parses the value. A value that isn't a number prints a warning
  to `System.err` and returns the default.
- `getBooleanProperty(name, default)` is true only for `true` (any case, surrounding spaces ignored).
  Any other value is false, the default is used when the property isn't defined.

### Changing the prefix

An object's prefix is its class name. In a sub class, `setPropertyPrefix(...)` (protected) uses a
different one, which is how several instances of one class get different settings:

```java
public class Database extends BaseObject {

    public Database(String name) {
        setPropertyPrefix(name);            // "primary", "reports", ...
    }

    String url() {
        return getProperty("Url");          // -Dprimary.Url=..., or "primary.Url = ..." in Database.properties
    }
}
```

`setSupportPrefixProperty(false)` turns off the prefixed lookups completely for one object (only
`<name>` is used). `getPropertyClass()` (protected) names the class whose files are searched, the
object's own class by default.

### Where the files can be

- **Class path** (the usual case). The file is found through the object's class. If more than one file
  of that name is on the class path they are **merged**: the first one on the class path wins for each
  key and the later ones fill in the keys it doesn't define. So an application can override one value
  of a library's `com/example/Mailer.properties` by putting its own `com/example/Mailer.properties`
  (with just that key) earlier on the class path, and the library's other defaults stay.
  To remove a default instead of replacing it, give the key an empty value (`SmtpHost =`).
- **Module path** (Java modules). A file in a package of a named module is encapsulated. `BaseObject`
  can't see it unless the module opens the package to `us.bringardner.parley.core`. A library in a named module
  can instead put its defaults in `META-INF/parley/<fully qualified class name>.properties`, for example
  `META-INF/parley/com.example.Mailer.properties`. That location is never encapsulated. It is also used on
  the class path when no `/com/example/Mailer.properties` is found.
  On the module path two modules can't contain the same package, so a file can't be replaced by one with
  the same name, use system properties or the file of the extending class.

### Caching

- Properties files are cached for each class loader and module (up to 1000 per loader by default, one per
  class searched, found or not), so a plugin's files don't mix with those of a class with the same name
  elsewhere.
- A file that wasn't found is cached too, so a file added later isn't seen until the cache is cleared.
- `BaseObject.setMaxProperties(n)` changes the limit (0 for none), and
  `BaseObject.clearPropertyCache()` makes every file be read again.

## Logging

Every `BaseObject` has `logDebug`, `logInfo`, `logWarn` and `logError` methods (each with a
`Throwable` variant), plus `isDebugEnabled()` and so on. The `Supplier` versions, such as
`logDebug(() -> "...")`, only build the message when that level is enabled.

The logger is chosen once, the first time one is needed:

1. The class named by the system property `ILogger`, if it implements `ILogger`
2. `Log4JLogger`, if log4j 2 (`log4j-api`) is on the class path
3. `ParleyLogger` otherwise

Loggers are shared by name (the class name by default), so objects of the same class use one
logger. `BaseObject.findLogger(name)` returns the logger for any name, and
`setLogger(...)` replaces it for one object. Levels are `DEBUG`, `INFO`, `WARN`, `ERROR` and `NONE`.

### ParleyLogger

The built-in logger writes `MM-dd-yyyy HH:mm:ss.SSS [thread] LEVEL name - message` lines.
It is configured with properties (see [Properties](#properties); the class name prefix is
`us.bringardner.parley.core.ParleyLogger`):

| Property | Meaning |
|---|---|
| `<logger name>.LogLevel` | The level for one logger, e.g. `-Dcom.example.Mailer.LogLevel=DEBUG` |
| `LogLevel` | The level for all loggers. The default is `ERROR`. |
| `LogFile` | A file to append to, or `System.out` (the default) or `System.err`. Loggers that name the same file share it. |
| `LogFileMaxSize` | Start a new log file when it would grow past this size: bytes, or with a `K`, `M` or `G` suffix (`10M`). The old file is renamed `LogFile.1` (the previous `.1` becomes `.2` and so on). The default is no limit. |
| `LogFileCount` | How many old log files to keep, 5 by default. With 0 the log file is started again from empty. |
| `LogFlushInterval` | Milliseconds between writes to the log file. The default is 0: every entry is written as soon as it is logged. With a value (100 to 1000 is typical) entries are collected and written together, which is about ten times cheaper for a busy logger. The last entries (up to that many milliseconds) are lost if the JVM is killed; they are written at a normal exit and by `ParleyLogger.closeLogFiles()`. |

The first logger to open a file decides its size limit. If writing to the log file fails (a full
disk, say), that is reported once on `System.err`, and again when writing works again.

If another program moves or deletes the log file (logrotate, say), a new one is started under the same
name within a second; a file emptied in place (`copytruncate`) is written to from its new end.
`ParleyLogger.closeLogFiles()` flushes and closes all log files so they can be moved (Windows won't move
an open file); the next entry opens the file again.

Level names from other frameworks also work: `OFF`, `FATAL`, `SEVERE`, `WARNING`, `TRACE`,
`ALL`, `FINE`, `FINER` and `FINEST`. An invalid level is reported and the default is used.

### log4j 2 and java.util.logging

`Log4JLogger` calls log4j through reflection, so parley-core does not depend on it. Add
`log4j-api` and `log4j-core` to your own project and configure log4j as usual
(`resources/log4j2.xml` is a working example). Setting a level from code needs `log4j-core`.

`JulLogger` uses `java.util.logging`. It reads the file named by `java.util.logging.config.file`,
or `/JulLogging.properties` from the class path. This happens once, when the first
`JulLogger` is created. `resources/JulLogging.properties` is an example.

## Threads and servers

`BaseThread` wraps a `Thread` that you can start, stop and start again. Your `run()` sets the
`started` and `running` flags and returns when `stopping` is set:

```java
public class Poller extends BaseThread {
    @Override
    public void run() {
        started = running = true;
        while (!stopping) {
            poll();
        }
        running = false;
    }
}

Poller poller = new Poller();
poller.setName("poller");
poller.start();
...
poller.stop(5000, true);   // ask it to stop, interrupt it, wait up to 5 seconds
```

Name, priority, daemon, context class loader and uncaught exception handler can be set before
`start()`. Threads are daemons by default. A running thread's daemon flag can't change, so
`setDaemon` takes effect the next time the thread starts.

### Virtual threads

parley-core is a multi-release jar. On Java 21 and later a `BaseThread` can run on a virtual thread:

- `setVirtual(true)` for one thread, or the system property
  `-Dus.bringardner.parley.core.virtualThreads=true` for all of them. The default is `false`, and
  the value `auto` uses virtual threads on Java 24 and later, where blocking socket I/O no longer pins
  the carrier thread (JEP 491).
- Before Java 21 the setting is accepted and ignored. `BaseThread.isVirtualSupported()` tells you which.
- A virtual thread is always a daemon thread at normal priority.
- `NamedThreadFactory` gives executors named threads: `new NamedThreadFactory("poller")` makes
  `poller`, `poller-2` ..., `NamedThreadFactory.numbered("worker-")` makes `worker-1`, `worker-2` ...

### Servers

`AbstractCoreServer` is a `BaseThread` for servers. `getServerSocket()` creates a plain or SSL
server socket from these properties (or the matching setters):

| Property | Default | Meaning |
|---|---|---|
| `Port` | `9200` | Port to listen on (`0` = any free port) |
| `BindAddress` | all addresses | Address to listen on |
| `Backlog` | `0` (system default) | Connection queue length |
| `AcceptTimeout` | `60000` ms | `accept()` timeout, so the loop can check `stopping` |
| `SocketTimeout` | `60000` ms | Read timeout set by `configure(socket)` |
| `IsSoLinger` / `SoLinger` | `false` / `10` s | SO_LINGER set by `configure(socket)` |
| `KeepAlive` | `false` | SO_KEEPALIVE set by `configure(socket)`, to notice peers that vanish |
| `TcpNoDelay` | `false` | TCP_NODELAY set by `configure(socket)`, so small writes aren't delayed |
| `MaxConnections` | `0` (no limit) | Limit used by `tryAcquireConnection()` (see below) |
| `MaxConnectionsPerClient` | `0` (no limit) | Connections one client address may have at once, so one host can't take every place (`tryAcquireClient(address)`, `tryAcquireConnection(address)`) |
| `AllowedClients` | everyone | Client addresses and networks allowed to connect, e.g. `127.0.0.1 10.0.0.0/8 ::1` (`isClientAllowed(address)`) |
| `LoginFailureDelay` | `1000` ms | How long a server should wait after a failed login (see below) |
| `MaxLoginAttempts` | `3` | Failed logins before the connection should be closed (see below) |
| `LoginTimeLimit` | `0` ms (no limit) | How long a connection may take to log in (see below) |
| `secure` | `false` | Use SSL (see [SSL](#ssl)) |

```java
AbstractCoreServer server = new AbstractCoreServer(8080) {
    @Override
    public void run() {
        try (ServerSocket ss = getServerSocket()) {
            started = running = true;
            while (!stopping) {
                try (Socket socket = ss.accept()) {
                    configure(socket);
                    handle(socket);
                } catch (SocketTimeoutException e) {
                    // check stopping again
                }
            }
        } catch (IOException e) {
            logError("Server failed", e);
        }
        running = false;
    }
};
server.start();
```

To limit how many connections are handled at once, an accept loop calls `tryAcquireConnection()`
for each accepted socket (closing it if that returns false) and `releaseConnection()` when the
connection ends. The server doesn't call them itself, so without that nothing is limited.

The login settings are the same kind of thing: `AbstractCoreServer` keeps them (`getLoginFailureDelay()`,
`getMaxLoginAttempts()`, `isTooManyLoginFailures(failures)`, `getLoginTimeLimit()`, and their setters), and
the protocol server that handles logins applies them. A server can change the defaults by overriding
`getDefaultLoginFailureDelay()` and `getDefaultMaxLoginAttempts()`.

`SocketClient` is the client side: `new SocketClient(useSSL).getSocket(host, port)` returns a
socket configured with the same `SocketTimeout`, `IsSoLinger`, `SoLinger`, `KeepAlive` and
`TcpNoDelay` properties.

## SSL

`SecureBaseObject` (and so `BaseThread`, `AbstractCoreServer` and `SocketClient`) builds an
`SSLContext` from these properties, or the matching setters:

| Property | Meaning |
|---|---|
| `KeyStoreName` | A class path resource (e.g. `/keys/server.p12`) or a file |
| `KeyStorePassword` | Without it no key managers are created, which is fine for a client |
| `KeyStoreType` | e.g. `PKCS12`. The default is the JVM default type (`PKCS12` since Java 9). |
| `Algorithm` | Key manager algorithm, e.g. `SunX509`. The default is the JVM default (`KeyManagerFactory.getDefaultAlgorithm()`). |
| `Protocol` | `SSLContext` protocol. The default is `TLS`. |

Trust managers can be set per object with `setTrustManagers(...)` or for all new objects with
`SecureBaseObject.setDefaultTrustManagers(...)`. `makecert.sh` creates a self-signed key store
for testing.

`TlsSockets` and `TrustAllCertificates` (see [Utilities](#utilities)) cover the client side. The system
property `ForceTlsVersion` (for example `-DForceTlsVersion=TLSv1.2`) is available through
`SecureBaseObject.getForcedTlsProtocols()`; parley-core doesn't apply it itself, the protocol
libraries that use it do.

## Utilities

All in `us.bringardner.parley.core.util`.

- **`TlsSockets`**: `layer(...)` puts TLS on a connected socket (STARTTLS, FTP's `AUTH TLS`),
  `configureClient(...)` sets SNI and the HTTPS host name check on an `SSLSocket` or `SSLEngine`, and
  `hostnameVerifying(factory)` wraps a socket factory so its sockets check the host name.
  `SocketClient.startTls(socket, host)` does the same with the client's own `SSLContext`.
- **`TrustAllCertificates`**: accepts every certificate. Only for tests and opportunistic TLS, never
  where the peer needs to be authenticated.
- **`AddressMatcher`**: `AddressMatcher.parse("10.0.0.0/8, 192.168.1.5, ::1")` then `matches(inetAddress)`.
  Only address literals are accepted, never a host name.
- **`PrivateKeys`**: `PrivateKeys.load(file, passphrase)` reads RSA and EC (P-256, P-384, P-521) keys
  from PKCS#8 and traditional PEM files, encrypted or not, and from PKCS12 and JKS key stores.
  `Pem` and `Der` are the readers it is built on. `PrivateKeys.writePrivateFile(file, text)` creates a
  new file only its owner can read (POSIX `rw-------`; on Windows the ACL is cut down to the owner),
  and `PrivateKeys.exposure(file)` says if an existing key file can be read by its group or everyone
  (`null` when it is private), so a server can warn before using it.
- **`Ec`**: `encodePoint` / `decodePoint` (with an on-curve check), `signatureToDer` and
  `signatureFromDer` to convert ECDSA signatures between Java's DER form and the raw r‖s form that
  DNSSEC, JOSE and SSH use.
- **`Base32Hex`**: `Base32Hex.encode(bytes)` / `decode(text)`, unpadded, case-insensitive on decode.
- **`RenamedProperty`**: `RenamedProperty.get("New.name", "Old.name", default)` for properties that
  were renamed: the new name wins, the old one still works and logs once that it is deprecated.
- **`Hex`**: `Hex.encode(bytes)`, `Hex.encode(bytes, true, ":")` for fingerprints, `Hex.decode(text)`.
- **`LogHelper`**: see [What's inside](#whats-inside).

## Building and testing

```bash
mvn verify
```

This compiles the library, runs the tests and writes a [JaCoCo](https://www.jacoco.org/)
coverage report to `target/site/jacoco/index.html`.

- Building needs **JDK 21 or later**: the Java 21 classes in `src/main/java21` go into
  `META-INF/versions/21`. The library itself still runs on Java 11.
- Tests named `*IT` run in `mvn verify` against the built jar, so on JDK 21+ they exercise the Java 21 classes.
- The SSL server test uses `keytool` (from the JDK) to create a test key store if needed.
- log4j is a test dependency, so the `Log4JLogger` tests run against the real library.

## Releasing

Set the new version in `pom.xml` (and the parent's, if it changed), then run
`../parley-build/release.sh` from this folder. It publishes to Maven Central with
`mvn -Prelease clean deploy`, then tags the release (`v<version>`) and pushes; finish by pressing
Publish on central.sonatype.com. See
[parley-parent](https://github.com/parley-project/parley-parent#releasing) for what a Central
release needs.

## License

Copyright 1998-2026 Tony Bringardner. Licensed under the [Apache License, Version 2.0](LICENSE).
