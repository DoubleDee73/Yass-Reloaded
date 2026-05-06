package yass.integration.cover.fanart

import spock.lang.Specification

class FanartTvCoverCandidateSpec extends Specification {

    def 'stores preview image url separately from original image url'() {
        when:
        def candidate = new FanartTvCoverCandidate(
                'https://assets.fanart.tv/fanart/music/albums/album-id/albumcover/brand-new-day-541c0f75b4e0b.jpg',
                'https://assets.fanart.tv/fanart/music/albums/album-id/albumcover/brand-new-day-541c0f75b4e0b.jpg',
                'Brand New Day',
                3,
                true
        )

        then:
        candidate.imageUrl == 'https://assets.fanart.tv/fanart/music/albums/album-id/albumcover/brand-new-day-541c0f75b4e0b.jpg'
        candidate.previewImageUrl == 'https://assets.fanart.tv/fanart/music/albums/album-id/albumcover/brand-new-day-541c0f75b4e0b.jpg'
        candidate.albumName == 'Brand New Day'
        candidate.likes == 3
        candidate.preferred
    }
}
