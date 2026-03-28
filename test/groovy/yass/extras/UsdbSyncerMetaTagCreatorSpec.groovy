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

package yass.extras

import spock.lang.Specification

class UsdbSyncerMetaTagCreatorSpec extends Specification {

    def 'restore keeps syncer tags from current video and old video from comment'() {
        when:
        def restored = UsdbSyncerMetaTagCreator.buildRestoreState(
                'v=tLJPsHhcEOo,key=Fm,co=https://i.ytimg.com/vi/Lsx_6jg22uI/maxresdefault.jpg,co-resize=720',
                '#VIDEO:Babyface - Give U My Heart feat. Toni Braxton.mp4|v=tLJPsHhcEOo,key=Fm'
        )

        then:
        restored.video == 'Babyface - Give U My Heart feat. Toni Braxton.mp4'
        restored.commentTag == 'v=tLJPsHhcEOo,key=Fm,co=https://i.ytimg.com/vi/Lsx_6jg22uI/maxresdefault.jpg,co-resize=720'
    }


    def 'restore also keeps non-syncer comment tags from legacy comma separated video marker'() {
        when:
        def restored = UsdbSyncerMetaTagCreator.buildRestoreState(
                'v=tLJPsHhcEOo,co=https://i.ytimg.com/vi/Lsx_6jg22uI/maxresdefault.jpg,co-resize=720',
                '#VIDEO:Babyface - Give U My Heart feat. Toni Braxton.mp4,v=tLJPsHhcEOo,key=Ab'
        )

        then:
        restored.video == 'Babyface - Give U My Heart feat. Toni Braxton.mp4'
        restored.commentTag.split(',') as Set == [
                'v=tLJPsHhcEOo',
                'co=https://i.ytimg.com/vi/Lsx_6jg22uI/maxresdefault.jpg',
                'co-resize=720',
                'key=Ab'
        ] as Set
    }


    def 'restore drops duet player tags from comment payload'() {
        when:
        def restored = UsdbSyncerMetaTagCreator.buildRestoreState(
                'v=tLJPsHhcEOo,p1=Babyface,p2=Toni Braxton,co=https://i.ytimg.com/vi/Lsx_6jg22uI/maxresdefault.jpg',
                '#VIDEO:Babyface - Give U My Heart feat. Toni Braxton.mp4|key=Ab'
        )

        then:
        restored.video == 'Babyface - Give U My Heart feat. Toni Braxton.mp4'
        restored.commentTag.split(',') as Set == [
                'v=tLJPsHhcEOo',
                'co=https://i.ytimg.com/vi/Lsx_6jg22uI/maxresdefault.jpg',
                'key=Ab'
        ] as Set
    }

    def 'save keeps preserved video and non-syncer comment tags while replacing syncer line'() {
        when:
        def saved = UsdbSyncerMetaTagCreator.buildSaveState(
                'Babyface - Give U My Heart feat. Toni Braxton.mp4',
                '#VIDEO:Babyface - Give U My Heart feat. Toni Braxton.mp4|v=tLJPsHhcEOo,key=Fm,co=https://i.ytimg.com/vi/Lsx_6jg22uI/maxresdefault.jpg,co-resize=720,foo=bar',
                'v=tLJPsHhcEOo,key=Fm,co=https://i.ytimg.com/vi/Lsx_6jg22uI/maxresdefault.jpg,co-resize=720'
        )

        then:
        saved.video == 'v=tLJPsHhcEOo,key=Fm,co=https://i.ytimg.com/vi/Lsx_6jg22uI/maxresdefault.jpg,co-resize=720'
        saved.commentTag == '#VIDEO:Babyface - Give U My Heart feat. Toni Braxton.mp4|key=Fm,foo=bar'
    }
}
