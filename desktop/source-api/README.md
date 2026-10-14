# Portable source API

This Kotlin/JVM module defines the platform-neutral source contract intended for
the Windows desktop client and its source server. It has no Android dependencies.
Plugins implement `SourcePlugin` and receive network access through
`SourceHttpClient`; image pages remain URLs plus request headers so the server
can fetch and stream them without loading whole chapters into memory.

Existing Mihon extensions are Android APKs. They use Android package metadata,
class loading, and Android-facing source APIs, so they cannot be loaded by this
JVM module as-is. Each source implementation intended for desktop must be
rebuilt against this API. Android can continue to use its existing extension
runtime independently.

Third-party source plugins execute code and must be treated as untrusted. A
desktop source host must require explicit trust/installation consent, constrain
network and file access, and avoid exposing an unauthenticated server to the
network.
