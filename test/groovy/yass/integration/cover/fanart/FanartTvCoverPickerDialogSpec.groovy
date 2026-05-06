package yass.integration.cover.fanart

import spock.lang.Specification

import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JToggleButton
import javax.swing.border.LineBorder
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicInteger

class FanartTvCoverPickerDialogSpec extends Specification {

    def 'loads preview only when button intersects viewport'() {
        expect:
        FanartTvCoverPickerDialog.shouldLoadPreview(new Rectangle(0, 0, 300, 300), new Rectangle(10, 10, 100, 100))
        !FanartTvCoverPickerDialog.shouldLoadPreview(new Rectangle(0, 0, 300, 300), new Rectangle(400, 10, 100, 100))
    }

    def 'falls back to original image when preview image request throws'() {
        given:
        def dialog = new FanartTvCoverPickerDialog(null, 'Title', 'Download', []) {
            @Override
            protected BufferedImage loadPreviewImageFromUrl(String imageUrl) throws Exception {
                if (imageUrl == 'https://images.fanart.tv/bigpreview/test.jpg') {
                    throw new java.net.ConnectException('Connection refused')
                }
                return new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)
            }
        }
        def candidate = new FanartTvCoverCandidate(
                'https://assets.fanart.tv/fanart/test.jpg',
                'https://images.fanart.tv/bigpreview/test.jpg',
                'Album',
                0,
                false
        )

        when:
        def image = dialog.loadPreviewImage(candidate)

        then:
        image != null
        image.width == 2
        image.height == 2
    }

    def 'returns null when preview and original image requests both throw'() {
        given:
        def dialog = new FanartTvCoverPickerDialog(null, 'Title', 'Download', []) {
            @Override
            protected BufferedImage loadPreviewImageFromUrl(String imageUrl) throws Exception {
                throw new java.net.ConnectException('Connection refused')
            }
        }
        def candidate = new FanartTvCoverCandidate(
                'https://assets.fanart.tv/fanart/test.jpg',
                'https://images.fanart.tv/bigpreview/test.jpg',
                'Album',
                0,
                false
        )

        when:
        def image = dialog.loadPreviewImage(candidate)

        then:
        image == null
    }

    def 'aborts further preview loading after too many connect exceptions'() {
        given:
        def calls = new AtomicInteger()
        def dialog = new FanartTvCoverPickerDialog(null, 'Title', 'Download', []) {
            @Override
            protected BufferedImage loadPreviewImageFromUrl(String imageUrl) throws Exception {
                calls.incrementAndGet()
                throw new java.net.ConnectException('Connection refused')
            }
        }
        def candidate = new FanartTvCoverCandidate(
                'https://assets.fanart.tv/fanart/test.jpg',
                'https://images.fanart.tv/bigpreview/test.jpg',
                'Album',
                0,
                false
        )

        when:
        dialog.loadPreviewImage(candidate)
        dialog.loadPreviewImage(candidate)
        def image = dialog.loadPreviewImage(candidate)

        then:
        image == null
        calls.get() == 6
    }

    def 'applies consistent tile styling for selected and unselected states'() {
        given:
        def button = new JToggleButton()
        def imageLabel = new JLabel()
        def textLabel = new JLabel()
        button.putClientProperty('fanart.preview.label', imageLabel)
        button.putClientProperty('fanart.text.label', textLabel)

        when:
        FanartTvCoverPickerDialog.applyTileStyle(button, false)

        then:
        button.background == FanartTvCoverPickerDialog.DEFAULT_TILE_BACKGROUND
        imageLabel.background == FanartTvCoverPickerDialog.DEFAULT_TILE_BACKGROUND
        textLabel.background == FanartTvCoverPickerDialog.DEFAULT_TILE_BACKGROUND
        button.border instanceof LineBorder
        ((LineBorder) button.border).lineColor == FanartTvCoverPickerDialog.DEFAULT_TILE_BORDER_COLOR

        when:
        FanartTvCoverPickerDialog.applyTileStyle(button, true)

        then:
        button.background == FanartTvCoverPickerDialog.SELECTED_TILE_BACKGROUND
        imageLabel.background == FanartTvCoverPickerDialog.SELECTED_TILE_BACKGROUND
        textLabel.background == FanartTvCoverPickerDialog.SELECTED_TILE_BACKGROUND
        button.border instanceof LineBorder
        ((LineBorder) button.border).lineColor == FanartTvCoverPickerDialog.SELECTED_TILE_BORDER_COLOR
    }

    def 'does not register the download button as dialog default button'() {
        when:
        def dialog = new FanartTvCoverPickerDialog(null, 'Title', 'Download', [])

        then:
        dialog.rootPane.defaultButton == null
    }

    def 'finds first matching search result when no previous match exists'() {
        expect:
        FanartTvCoverPickerDialog.findNextMatchingIndex(
                ['First Album', 'Second Album', 'Another First'],
                'first',
                -1
        ) == 0
    }

    def 'cycles to the next matching search result on repeated enter'() {
        expect:
        FanartTvCoverPickerDialog.findNextMatchingIndex(
                ['First Album', 'Second Album', 'Another First'],
                'first',
                0
        ) == 2
    }

    def 'wraps search to the first matching result after the last match'() {
        expect:
        FanartTvCoverPickerDialog.findNextMatchingIndex(
                ['First Album', 'Second Album', 'Another First'],
                'first',
                2
        ) == 0
    }
}
