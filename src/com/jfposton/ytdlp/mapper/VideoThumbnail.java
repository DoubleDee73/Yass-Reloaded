package com.jfposton.ytdlp.mapper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoThumbnail {
  private String url;
  private String id;

  public String getId() {
    return id;
  }

  public String getUrl() {
    return url;
  }
}
