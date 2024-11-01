import javax.sound.sampled.*;
import java.util.logging.Logger;

public class MixerMain {
    private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    public static void main(String[] argv) {
        Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();
        if (mixerInfo.length == 0)
            LOGGER.info(" Error: No mixers available");
        else
        for (Mixer.Info mi : mixerInfo) {
            Mixer mixer = AudioSystem.getMixer(mi);
            // e.g. com.sun.media.sound.DirectAudioDevice
            LOGGER.info("Mixer: " + mixer.getClass().getName());
            Line.Info[] lis = mixer.getSourceLineInfo();
            for (Line.Info li : lis) {
                LOGGER.info("    Source line: " + li.toString());
                showFormats(li);
            }
            lis = mixer.getTargetLineInfo();
            for (Line.Info li : lis) {
                LOGGER.info("    Target line: " + li.toString());
                showFormats(li);
            }
            Control[] cs = mixer.getControls();
            for (Control c : cs) {
                LOGGER.info("    Control: " + c.toString());
            }
        }
    }
    private static void showFormats(Line.Info li) {
        if (li instanceof DataLine.Info) {
            AudioFormat[] afs = ((DataLine.Info) li).getFormats();
            for (AudioFormat af : afs) {
                LOGGER.info("        " + af.toString());
            }
        }
    }
}