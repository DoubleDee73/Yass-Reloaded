/*
 * Yass Reloaded - Karaoke Editor
 * Copyright (C) 2009-2023 Saruta
 * Copyright (C) 2023 DoubleDee
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package yass;

public class UsdbSyncerMetaFile {
    private int song_id;
    private String meta_tags;
    private boolean pinned;
    private UsdbFile txt;
    private UsdbFile audio;
    private UsdbFile video;
    private UsdbFile cover;
    private UsdbFile background;
    private int version;

    public int getSongId() {
        return song_id;
    }

    public void setSongId(int song_id) {
        this.song_id = song_id;
    }

    public String getMetaTags() {
        return meta_tags;
    }

    public void setMetaTags(String meta_tags) {
        this.meta_tags = meta_tags;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public UsdbFile getTxt() {
        return txt;
    }

    public void setTxt(UsdbFile txt) {
        this.txt = txt;
    }

    public UsdbFile getAudio() {
        return audio;
    }

    public void setAudio(UsdbFile audio) {
        this.audio = audio;
    }

    public UsdbFile getVideo() {
        return video;
    }

    public void setVideo(UsdbFile video) {
        this.video = video;
    }

    public UsdbFile getCover() {
        return cover;
    }

    public void setCover(UsdbFile cover) {
        this.cover = cover;
    }

    public UsdbFile getBackground() {
        return background;
    }

    public void setBackground(UsdbFile background) {
        this.background = background;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
