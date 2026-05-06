package yass.integration.cover.fanart

import com.google.gson.Gson
import com.google.gson.JsonObject
import spock.lang.Specification
import yass.YassSong
import yass.musicbrainz.Artist
import yass.musicbrainz.MusicBrainz
import yass.musicbrainz.MusicBrainzInfo
import yass.musicbrainz.Recording
import yass.musicbrainz.Release
import yass.musicbrainz.ReleaseGroup

class FanartTvCoverSearchServiceSpec extends Specification {

    def 'uses original fanart asset url as preview source'() {
        expect:
        FanartTvCoverSearchService.toPreviewImageUrl(
                'https://assets.fanart.tv/fanart/music/albums/album-id/albumcover/brand-new-day-541c0f75b4e0b.jpg'
        ) == 'https://assets.fanart.tv/fanart/music/albums/album-id/albumcover/brand-new-day-541c0f75b4e0b.jpg'
    }

    def 'returns null preview url when original url is blank'() {
        expect:
        FanartTvCoverSearchService.toPreviewImageUrl(null) == null
        FanartTvCoverSearchService.toPreviewImageUrl('') == null
        FanartTvCoverSearchService.toPreviewImageUrl('   ') == null
    }

    def 'resolves missing fanart album names via musicbrainz lookup'() {
        given:
        def service = new FanartTvCoverSearchService('api-key') {
            @Override
            protected String resolveAlbumTitleFromMusicBrainz(String albumId) {
                return albumId == 'fanart-album-id' ? "(I'm Gonna Be) 500 Miles" : null
            }
        }

        expect:
        service.resolveAlbumName('fanart-album-id', null, [:], [:], '').albumName == "(I'm Gonna Be) 500 Miles"
    }

    def 'falls back to song album when fanart and musicbrainz have no title'() {
        given:
        def service = new FanartTvCoverSearchService('api-key') {
            @Override
            protected String resolveAlbumTitleFromMusicBrainz(String albumId) {
                return null
            }
        }

        expect:
        service.resolveAlbumName('fanart-album-id', null, [:], [:], 'Song Album').albumName == 'Song Album'
    }

    def 'search prefers fanart artist results matched against musicbrainz ids'() {
        given:
        def musicBrainz = new StubMusicBrainz()
        def service = new FanartTvCoverSearchService('api-key', musicBrainz) {
            @Override
            protected JsonObject fetchJson(String baseUrl) {
                if (baseUrl == 'https://webservice.fanart.tv/v3/music/artist-id') {
                    return new Gson().fromJson('''
                        {
                          "albums": {
                            "release-group-id": {
                              "albumcover": [
                                {
                                  "id": "460878",
                                  "likes": "2",
                                  "url": "https://assets.fanart.tv/fanart/im-gonna-be-500-miles-694b0d8eb8741.jpg"
                                }
                              ],
                              "albumcover_count": 1
                            }
                          }
                        }
                    ''', JsonObject)
                }
                throw new AssertionError("Unexpected endpoint: " + baseUrl)
            }
        }
        def song = new YassSong('', '', '', 'The Proclaimers', "(I'm Gonna Be) 500 Miles")
        song.setAlbum("Song Album")

        when:
        def results = service.search(song)

        then:
        results.size() == 1
        results[0].albumName == "(I'm Gonna Be) 500 Miles"
        results[0].preferred
    }

    def 'search resolves fanart albums from broader artist discography when recording releases do not contain them'() {
        given:
        def musicBrainz = new StubMusicBrainz() {
            @Override
            List<ReleaseGroup> getArtistReleaseGroups(String artistId) {
                [releaseGroup('other-release-group-id', 'Now That’s What I Call Comic Relief')]
            }

            @Override
            List<Release> getArtistReleases(String artistId) {
                []
            }
        }
        def service = new FanartTvCoverSearchService('api-key', musicBrainz) {
            @Override
            protected JsonObject fetchJson(String baseUrl) {
                if (baseUrl == 'https://webservice.fanart.tv/v3/music/artist-id') {
                    return new Gson().fromJson('''
                        {
                          "albums": {
                            "other-release-group-id": {
                              "albumcover": [
                                {
                                  "id": "1",
                                  "likes": "0",
                                  "url": "https://assets.fanart.tv/fanart/now-thats-what-i-call-comic-relief.jpg"
                                }
                              ]
                            }
                          }
                        }
                    ''', JsonObject)
                }
                throw new AssertionError("Unexpected endpoint: " + baseUrl)
            }
        }
        def song = new YassSong('', '', '', 'The Proclaimers', "(I'm Gonna Be) 500 Miles")
        song.setAlbum("Song Album")

        when:
        def results = service.search(song)

        then:
        results.size() == 1
        results[0].albumName == 'Now That’s What I Call Comic Relief'
        !results[0].preferred
    }

    private static class StubMusicBrainz extends MusicBrainz {
        @Override
        MusicBrainzInfo queryMusicBrainz(String artist, String title) {
            def recording = new Recording()
            recording.setArtistCredit([artistCredit('artist-id', artist)])
            recording.setReleases([release('release-id', "(I'm Gonna Be) 500 Miles", 'release-group-id', "(I'm Gonna Be) 500 Miles")])

            def info = new MusicBrainzInfo()
            info.setRecording(recording)
            return info
        }
    }

    private static Release release(String releaseId, String releaseTitle, String releaseGroupId, String releaseGroupTitle) {
        def release = new Release()
        release.setId(releaseId)
        release.setTitle(releaseTitle)
        def releaseGroup = new ReleaseGroup()
        releaseGroup.setId(releaseGroupId)
        releaseGroup.setTitle(releaseGroupTitle)
        release.setReleaseGroup(releaseGroup)
        return release
    }

    private static ReleaseGroup releaseGroup(String id, String title) {
        def releaseGroup = new ReleaseGroup()
        releaseGroup.setId(id)
        releaseGroup.setTitle(title)
        return releaseGroup
    }

    private static yass.musicbrainz.ArtistCredit artistCredit(String artistId, String artistName) {
        def artist = new Artist()
        artist.setId(artistId)
        artist.setName(artistName)
        def credit = new yass.musicbrainz.ArtistCredit()
        credit.setArtist(artist)
        return credit
    }
}
