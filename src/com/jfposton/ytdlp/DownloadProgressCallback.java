package com.jfposton.ytdlp;

public interface DownloadProgressCallback {
  void onProgressUpdate(float progress, long etaInSeconds);
}
