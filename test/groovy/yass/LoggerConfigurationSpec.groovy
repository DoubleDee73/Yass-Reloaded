package yass

import spock.lang.Specification
import yass.integration.cover.fanart.FanartTvCoverPickerDialog
import yass.integration.cover.fanart.FanartTvCoverSearchService
import yass.usdb.UsdbClient
import yass.usdb.UsdbFirefoxCookieImporter
import yass.usdb.UsdbPythonCookieImporter
import yass.usdb.UsdbSessionService
import yass.usdb.UsdbSongAddService
import yass.usdb.UsdbSongCommentService
import yass.usdb.UsdbSongEditService

import java.lang.reflect.Field
import java.util.logging.Logger

class LoggerConfigurationSpec extends Specification {

    def 'selected classes use the global java util logger'() {
        expect:
        loggerNameFor(FanartTvCoverPickerDialog) == Logger.GLOBAL_LOGGER_NAME
        loggerNameFor(FanartTvCoverSearchService) == Logger.GLOBAL_LOGGER_NAME
        loggerNameFor(UsdbClient) == Logger.GLOBAL_LOGGER_NAME
        loggerNameFor(UsdbFirefoxCookieImporter) == Logger.GLOBAL_LOGGER_NAME
        loggerNameFor(UsdbPythonCookieImporter) == Logger.GLOBAL_LOGGER_NAME
        loggerNameFor(UsdbSessionService) == Logger.GLOBAL_LOGGER_NAME
        loggerNameFor(UsdbSongAddService) == Logger.GLOBAL_LOGGER_NAME
        loggerNameFor(UsdbSongCommentService) == Logger.GLOBAL_LOGGER_NAME
        loggerNameFor(UsdbSongEditService) == Logger.GLOBAL_LOGGER_NAME
    }

    private static String loggerNameFor(Class<?> type) {
        Field field = type.getDeclaredField('LOGGER')
        field.accessible = true
        return (field.get(null) as Logger).name
    }
}
