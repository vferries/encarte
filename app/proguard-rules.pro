# Intentionally empty: every library used here ships its own consumer R8 rules
# (kotlinx-serialization, Room, zxing-cpp, androidx). Verified on a release build:
# Navigation 3 back stack (reflective NavKey serialization) survives rotation and
# process death with R8 full mode. Keep this file: AGP 9 fails on missing proguard files.
