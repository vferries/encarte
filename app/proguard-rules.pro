# Most libraries ship their own consumer R8 rules (kotlinx-serialization, Room, zxing-cpp, androidx).
# Verified on a release build: Navigation 3 back stack (reflective NavKey serialization)
# survives rotation and process death with R8 full mode. Keep this file: AGP 9 fails on missing proguard files.

# commons-csv uses edu.umd.cs.findbugs annotations which are not in the runtime classpath.
# The annotations are optional and don't affect app behavior.
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
