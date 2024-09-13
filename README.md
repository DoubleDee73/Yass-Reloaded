
# Yass Reloaded Karaoke Editor
Yass Reloaded is a graphical editor for finetuning Ultrastar karaoke songs. 
You can drag & drop notes, spread syllables, and find errors. Further you can filter your song library, mass-tag or batch-correct them, or search for lyrics.

Yass Reloaded is a fork from Yass 2.4.3 that Saruta has been developing since 2009. Due to time constraints on his behalf and "creative differences" with the other contributors, I decided to go rogue with my fork.



# Downloads
Binaries can be found in the [Release Section](https://github.com/DoubleDee73/Yass/releases)

Min. Requirement is Java 21 with JavaFX (e. g. Open JDK ZuluFX https://www.azul.com/downloads/)

For Yass Reloaded to offer fully fledged audio format support, it is recommended, that you install FFmpeg:
https://www.ffmpeg.org/download.html
- Windows (<code>winget install --id=Gyan.FFmpeg -e</code>) - Check out: https://www.gyan.dev/ffmpeg/builds/
- MacOS (<code>brew install ffmpeg</code>)
- Linux (<code>sudo apt install ffmpeg</code>)
While Yass also works without FFmpeg, there are known issues, such as:
- On Windows, if using MP3 or M4a and if the GAP is less than 1000 (aka singing starts immediately), the notes will seem out-of-sync
- On Mac, notes are generally out-of-sync, unless WAV is used

Also, it is highly recommended to download Ultrastar Creator to get you started (https://github.com/UltraStar-Deluxe/UltraStar-Creator)

# Starting Yass Reloaded
If you install Yass Reloaded with the Windows installer, you can simply run <code>Yass.exe</code>

Alternatively, you can download the jar file and start it from the console:

On Windows, you can simply do this: <code>java -jar Yass-Reloaded-2024.9.jar</code>

On Mac/Linux, it is recommended, that you add a runtime variable like so:

<code>export PATH_TO_FX=path/to/javafx-sdk-[whateverversion]/lib</code>

and then start the jar like so:

<code>java --module-path $PATH_TO_FX --add-modules javafx.swing,javafx.media -jar Yass-Reloaded-2024.9.jar</code>

# Developers

Basic setup is described in the Wiki: https://github.com/SarutaSan72/Yass/wiki/Developers

# Yass uses
- Java FX
- Java Look & Feel Graphics Repository
- FFmpeg
- iText
- Jazzy Spell Checker
- JInput
- TeX Hyphenator
- [Optimaize Language Detector](https://github.com/optimaize/language-detector)
- Java Media Framework (JMF)
- Robert Eckstein's Wizard code
- juniversalchardet

Licenses are stated in the application's help section.

Speed measure 'Inverse Duration' based on Marcel Taeumel's approach (http://uman.sf.net).

# License

Copyright (C) 2009-2022 Saruta, 2024 DoubleDee 

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

# Troubleshooting
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
