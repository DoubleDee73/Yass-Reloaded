package yass.integration.transcription.whisperx

import spock.lang.Specification
import yass.YassProperties

import java.time.Duration

class WhisperXTranscriptionServiceSpec extends Specification {

    def "evaluateTimeoutState stays alive while output is fresh"() {
        given:
        def service = new TestableWhisperXTranscriptionService(Duration.ofMinutes(45), Duration.ofMinutes(5))
        service.nowNanos = Duration.ofMinutes(4).toNanos()

        expect:
        service.evaluateTimeoutState(0L, Duration.ofMinutes(1).toNanos()) == WhisperXTranscriptionService.TimeoutState.NONE
    }

    def "evaluateTimeoutState detects idle timeout before hard limit"() {
        given:
        def service = new TestableWhisperXTranscriptionService(Duration.ofMinutes(45), Duration.ofMinutes(5))
        service.nowNanos = Duration.ofMinutes(8).toNanos()

        expect:
        service.evaluateTimeoutState(0L, Duration.ofMinutes(2).toNanos()) == WhisperXTranscriptionService.TimeoutState.IDLE
    }

    def "evaluateTimeoutState detects hard limit regardless of recent output"() {
        given:
        def service = new TestableWhisperXTranscriptionService(Duration.ofMinutes(45), Duration.ofMinutes(5))
        service.nowNanos = Duration.ofMinutes(46).toNanos()

        expect:
        service.evaluateTimeoutState(0L, Duration.ofMinutes(45).toNanos()) == WhisperXTranscriptionService.TimeoutState.HARD_LIMIT
    }

    def "buildTimeoutMessage includes last WhisperX output"() {
        given:
        def service = new TestableWhisperXTranscriptionService(Duration.ofMinutes(45), Duration.ofMinutes(5))

        when:
        def message = service.buildTimeoutMessage(WhisperXTranscriptionService.TimeoutState.IDLE,
                "[stderr] Downloading alignment model")

        then:
        message.contains("produced no output for 5 minutes")
        message.contains("Last WhisperX output")
        message.contains("Downloading alignment model")
    }

    private static class TestableWhisperXTranscriptionService extends WhisperXTranscriptionService {
        private final Duration processTimeout
        private final Duration idleTimeout
        long nowNanos

        TestableWhisperXTranscriptionService(Duration processTimeout, Duration idleTimeout) {
            super(new YassProperties())
            this.processTimeout = processTimeout
            this.idleTimeout = idleTimeout
        }

        @Override
        long currentNanoTime() {
            return nowNanos
        }

        @Override
        Duration getProcessTimeout() {
            return processTimeout
        }

        @Override
        Duration getIdleTimeout() {
            return idleTimeout
        }
    }
}
