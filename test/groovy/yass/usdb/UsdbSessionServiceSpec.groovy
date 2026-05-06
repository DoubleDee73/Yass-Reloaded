package yass.usdb

import spock.lang.Specification

import java.io.IOException

class UsdbSessionServiceSpec extends Specification {

    def 'session info stops retrying after three io failures until explicitly reset'() {
        given:
        def service = new StubUsdbSessionService([
                new IOException('offline-1'),
                new IOException('offline-2'),
                new IOException('offline-3'),
                "<span class='gen'>Willkommen <b>TestUser</b></span><a href='?link=editsongs'>edit</a><img src='images/rank_5.gif'>"
        ])

        when:
        service.getSessionInfo()

        then:
        thrown(IOException)
        service.requestCount == 1

        when:
        service.getSessionInfo()

        then:
        thrown(IOException)
        service.requestCount == 2

        when:
        service.getSessionInfo()

        then:
        thrown(IOException)
        service.requestCount == 3

        when:
        def blockedInfo = service.getSessionInfo()

        then:
        !blockedInfo.loggedInUser()
        !blockedInfo.directEditAllowed()
        blockedInfo.rank() == null
        service.requestCount == 3

        when:
        service.resetSessionInfoRetryLimit()
        def recoveredInfo = service.getSessionInfo()

        then:
        recoveredInfo.loggedInUser() == 'TestUser'
        recoveredInfo.directEditAllowed()
        recoveredInfo.rank() == 5
        service.requestCount == 4
    }

    def 'successful session lookup resets the consecutive failure counter'() {
        given:
        def service = new StubUsdbSessionService([
                new IOException('offline-1'),
                new IOException('offline-2'),
                "<span class='gen'>Willkommen <b>Recovered</b></span>",
                new IOException('offline-3'),
                new IOException('offline-4'),
                new IOException('offline-5')
        ])

        when:
        service.getSessionInfo()

        then:
        thrown(IOException)

        when:
        service.getSessionInfo()

        then:
        thrown(IOException)

        when:
        def info = service.getSessionInfo()

        then:
        info.loggedInUser() == 'Recovered'

        when:
        service.getSessionInfo()

        then:
        thrown(IOException)
        service.requestCount == 4

        when:
        service.getSessionInfo()

        then:
        thrown(IOException)
        service.requestCount == 5

        when:
        service.getSessionInfo()

        then:
        thrown(IOException)
        service.requestCount == 6

        when:
        def blockedInfo = service.getSessionInfo()

        then:
        !blockedInfo.isLoggedIn()
        service.requestCount == 6
    }

    def 'cached session info returns the last successful session without another request'() {
        given:
        def service = new StubUsdbSessionService([
                "<span class='gen'>Willkommen <b>CachedUser</b></span><a href='?link=editsongs'>edit</a><img src='images/rank_7.gif'>"
        ])

        when:
        def freshInfo = service.getSessionInfo()
        def cachedInfo = service.getCachedSessionInfo()

        then:
        freshInfo.loggedInUser() == 'CachedUser'
        cachedInfo.loggedInUser() == 'CachedUser'
        cachedInfo.directEditAllowed()
        cachedInfo.rank() == 7
        service.requestCount == 1
    }

    def 'logout clears cached session info'() {
        given:
        def service = new StubUsdbSessionService([
                "<span class='gen'>Willkommen <b>CachedUser</b></span><a href='?link=editsongs'>edit</a>"
        ])

        when:
        service.getSessionInfo()
        service.logout()

        then:
        !service.getCachedSessionInfo().isLoggedIn()
        service.requestCount == 1
    }

    private static class StubUsdbSessionService extends UsdbSessionService {
        private final List<Object> responses
        int requestCount = 0

        StubUsdbSessionService(List<Object> responses) {
            this.responses = new ArrayList<>(responses)
        }

        @Override
        String get(String path, Map<String, String> params) throws IOException {
            requestCount++
            def next = responses.remove(0)
            if (next instanceof IOException) {
                throw next
            }
            return next as String
        }
    }
}
