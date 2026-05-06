package yass.integration.cover.fanart

import spock.lang.Specification

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class FanartTvCoverSearchServiceLoggingSpec extends Specification {

    def 'abbreviates long response bodies for debug logging'() {
        given:
        def longText = 'x' * 4105

        when:
        def abbreviated = FanartTvCoverSearchService.abbreviateForLog(longText)

        then:
        abbreviated.size() < longText.size()
        abbreviated.startsWith('x' * 4000)
        abbreviated.contains('...(truncated ')
    }

    def 'keeps short response bodies unchanged for debug logging'() {
        expect:
        FanartTvCoverSearchService.abbreviateForLog('{"albums":{}}') == '{"albums":{}}'
    }

    def 'formats response headers into a compact debug string'() {
        given:
        def headers = [
                'Content-Type': ['application/json'],
                'CF-Cache-Status': ['DYNAMIC'],
                null             : ['HTTP/1.1 403 Forbidden']
        ]

        when:
        def formatted = FanartTvCoverSearchService.formatHeadersForLog(headers)

        then:
        formatted.contains('Status=HTTP/1.1 403 Forbidden')
        formatted.contains('Content-Type=application/json')
        formatted.contains('CF-Cache-Status=DYNAMIC')
    }

    def 'summarizes collections for compact debug logging'() {
        expect:
        FanartTvCoverSearchService.summarizeCollectionForLog(['one', 'two', 'three']) == '[one, two, three]'
        FanartTvCoverSearchService.summarizeCollectionForLog(['one', 'two', 'three', 'four', 'five'])
                .startsWith('[one, two, three, four')
    }

    def 'reads full response body without truncating parse input'() {
        given:
        def body = '{"payload":"' + ('x' * 5000) + '"}'
        def inputStream = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))

        when:
        def readBody = FanartTvCoverSearchService.readResponseBody(inputStream)

        then:
        readBody == body
        readBody.size() > 4000
    }
}
