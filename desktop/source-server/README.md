# Portable source server

The server hosts trusted `SourcePluginProvider` implementations on the local
machine and exposes a minimal JSON API. It binds only to `127.0.0.1` by
default; do not change this to a network interface without adding authentication
and explicit access controls.

Build and run with:

```sh
./gradlew :desktop:source-server:installDist
desktop/source-server/build/install/source-server/bin/source-server
```

`MIHON_SOURCE_SERVER_PORT` can select a listening port from 1 to 65535. Source
providers are discovered using Java `ServiceLoader` and must be on the server
runtime classpath. Android APK extensions are not supported by this runtime;
desktop plugins must be rebuilt against `desktop:source-api`.
