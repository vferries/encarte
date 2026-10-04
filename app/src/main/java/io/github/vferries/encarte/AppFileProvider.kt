package io.github.vferries.encarte

import androidx.core.content.FileProvider

/** Shares the camera capture file with the system camera app (FileProvider docs recommend a subclass). */
class AppFileProvider : FileProvider(R.xml.file_paths)
