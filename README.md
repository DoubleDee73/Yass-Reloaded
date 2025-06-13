
# Yass Reloaded Karaoke Editor
Yass Reloaded is a graphical editor for finetuning Ultrastar karaoke songs. 
You can drag & drop notes, spread syllables, and find errors. Further you can filter your song library, mass-tag or batch-correct them, or search for lyrics.

Yass Reloaded is a fork from Yass 2.4.3 that Saruta has been developing since 2009. Due to time constraints on his behalf and "creative differences" with the other contributors, I decided to go rogue with my fork.

# Main Differences to the original Yass
- Better support for audio formats. Note, that FFmpeg is now required, so support any audio format that FFmpeg supports, such as AAC, OGG or OPUS
- Improved UX: Coming from someone who has created hundreds of songs, many little tweaks and improvements have gone into Yass Reloaded to make things easier
  - Better copy-pasting
  - BPM, GAP, START, END tags can now directly be edited without having to open additional dialogs
- Faster bugfixing: Many issues, that are still open in the original Yass have already been dealt with in Yass Reloaded. Any new issues that arise will also be reviewed and fixed
- The Ultrastar community has decided to enhance the current Ultrastar format by adding new tags. Theses enhancements are also constantly incorporated into the software.
- Is developed in Java 21 instead of Java 8. Third party libraries will be kept up-to-date

# Downloads
Binaries can be found in the [Release Section](https://github.com/DoubleDee73/Yass/releases)

Min. Requirement is Java 21 (e. g. Open JDK ZuluFX https://www.azul.com/downloads/).
While JavaFX is no longer required, it may be needed in future releases.

Yass Reloaded requires FFmpeg to support a variety of audio formats.
https://www.ffmpeg.org/download.html
- Windows (<code>winget install --id=Gyan.FFmpeg -e</code>) - Check out: https://www.gyan.dev/ffmpeg/builds/
- MacOS (<code>brew install ffmpeg</code>)
- Linux (<code>sudo apt install ffmpeg</code>)

Also, it is highly recommended to download Ultrastar Creator to get you started (https://github.com/UltraStar-Deluxe/UltraStar-Creator)

# Starting Yass Reloaded
If you install Yass Reloaded with the Windows installer, you can simply run <code>Yass.exe</code>

Alternatively, you can download the jar file and start it from the console:

On Windows, you can simply do this: <code>java -jar Yass-Reloaded-2025.6.jar</code>

On Mac/Linux, it is recommended, that you add a runtime variable like so:

<code>export PATH_TO_FX=path/to/javafx-sdk-[whateverversion]/lib</code>

and then start the jar like so:

<code>java --module-path $PATH_TO_FX --add-modules javafx.swing,javafx.media -jar Yass-Reloaded-2025.6.jar</code>

# Developers

Basic setup is described in the Wiki: https://github.com/SarutaSan72/Yass/wiki/Developers

# Yass Reloaded uses
- Java Look & Feel Graphics Repository
- FFmpeg
- [jAudiotagger](https://www.jthink.net/jaudiotagger)
- [FFmpeg CLI Wrapper for Java](https://github.com/bramp/ffmpeg-cli-wrapper)
- iText
- Jazzy Spell Checker
- JInput
- TeX Hyphenator
- [Optimaize Language Detector](https://github.com/optimaize/language-detector)
- [JAudiotagger](https://www.jthink.net/jaudiotagger/)
- Java Media Framework (JMF)
- Robert Eckstein's Wizard code
- juniversalchardet

Licenses are stated in the application's help section.

Speed measure 'Inverse Duration' based on Marcel Taeumel's approach (http://uman.sf.net).

# License

Copyright (C) 2009-2023 Saruta, 2023-2025 DoubleDee 

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.

# Support
If you like this  app, feel free (but not pressured!) to buy me coffee. Any support is very much appreciated!

<a target="_blank" rel="noopener noreferrer" href="https://www.buymeacoffee.com/DoubleDee73"><img src="https://github.com/user-attachments/assets/a40f851a-2ef1-46ce-a6d6-0bf6fd0ffb95" alt="image" style="max-width: 100%; width: 200px;"></a>

# Troubleshooting
## Yass Reloaded does nothing after showing the splash screen
- Have you checked your Java version? 
  - Open a command line window and type in <code>java -version</code>. 
  - You should have Java 21
- Have you tried opening it as a jar?
  - Download the jar, open a command line window and type in <code>java -jar Yass-Reloaded-2025.6.jar</code>
  - Any error messages are then printed out in the command line window for debugging

## Cannot find FFmpeg?
Yass Reloaded has a built-in auto-detection for FFmpeg. 
It will look in any PATH environment variables and try to find both ffmpeg and ffprobe.  
If you are sure, you have installed ffmpeg, maybe you did not choose to have the PATH variable automatically configured.  
If this is the cae, then it is recommended, that you add the FFmpeg path to your PATH variables

You can also add the path to FFmpeg to the Yass Reloaded configuration file. The user.xml should be in the ~/.yass/ folder

There should be an entry:

<code>&lt;entry key="ffmpegPath">&lt;/entry></code>

If it's not, add it, and put the FFmpeg path before &lt;/entry>, e. g.:

<code>&lt;entry key="ffmpegPath">/usr/bin&lt;/entry></code>

or

<code>&lt;entry key="ffmpegPath">C:\ffmpeg\bin&lt;/entry></code>

depending on your OS.

It is also possible, that when you use Automator on Mac to start Yass Reloaded, 
that due to restrictions to the PATH variables, the auto-detection fails.

In this case, start Yass Reloaded from the console. If FFmpeg is then detected, the path
will be written into the user.xml, and you can use Automator again.
