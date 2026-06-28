# govarnam JNI bridge

`jni.c` is vendored verbatim from
[govarnam-java](https://github.com/varnamproject/govarnam-java)
(`src/com/varnamproject/govarnam/jni.c`), © Subin Siby, **LGPL-3.0**.

It is the JNI glue between the engine's Java API — vendored at
`java/src/com/varnamproject/govarnam/` — and the native govarnam library. `make varnam-native`
compiles it against `libgovarnam.so` (built from the `govarnam/` submodule) into
`libgovarnam_jni.so`.
