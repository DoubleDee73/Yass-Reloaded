package com.jfposton.ytdlp.mapper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoFormat {

  private int asr;
  private int tbr;
  private int abr;
  private String format;

  @JsonProperty("format_id")
  private String formatId;

  @JsonProperty("format_note")
  private String formatNote;

  private String ext;
  private int preference;
  private String vcodec;
  private String acodec;
  private int width;
  private int height;
  private long filesize;
  private int fps;
  private String url;

  public int getAsr() {
    return asr;
  }

  public void setAsr(int asr) {
    this.asr = asr;
  }

  public int getTbr() {
    return tbr;
  }

  public void setTbr(int tbr) {
    this.tbr = tbr;
  }

  public int getAbr() {
    return abr;
  }

  public void setAbr(int abr) {
    this.abr = abr;
  }

  public String getFormat() {
    return format;
  }

  public void setFormat(String format) {
    this.format = format;
  }

  public String getFormatId() {
    return formatId;
  }

  public void setFormatId(String formatId) {
    this.formatId = formatId;
  }

  public String getFormatNote() {
    return formatNote;
  }

  public void setFormatNote(String formatNote) {
    this.formatNote = formatNote;
  }

  public String getExt() {
    return ext;
  }

  public void setExt(String ext) {
    this.ext = ext;
  }

  public int getPreference() {
    return preference;
  }

  public void setPreference(int preference) {
    this.preference = preference;
  }

  public String getVcodec() {
    return vcodec;
  }

  public void setVcodec(String vcodec) {
    this.vcodec = vcodec;
  }

  public String getAcodec() {
    return acodec;
  }

  public void setAcodec(String acodec) {
    this.acodec = acodec;
  }

  public int getWidth() {
    return width;
  }

  public void setWidth(int width) {
    this.width = width;
  }

  public int getHeight() {
    return height;
  }

  public void setHeight(int height) {
    this.height = height;
  }

  public long getFilesize() {
    return filesize;
  }

  public void setFilesize(long filesize) {
    this.filesize = filesize;
  }

  public int getFps() {
    return fps;
  }

  public void setFps(int fps) {
    this.fps = fps;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }
}
