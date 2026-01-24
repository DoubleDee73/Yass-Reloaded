<meta name="description" content="Yass Reloaded is a free, open-source Karaoke editor for Ultrastar songs. Finetune notes, edit lyrics, and manage your song library. Available for Windows, macOS, and Linux.">
<meta name="keywords" content="Yass, Yass Reloaded, Karaoke, Karaoke Editor, Ultrastar, Ultrastar Deluxe, Song Editor, Lyrics Editor, Open Source, Java, Windows, macOS, Linux">

# Yass Reloaded – Open-Source Karaoke Editor for UltraStar Songs

## What is Yass Reloaded?

Yass Reloaded is a free, open-source karaoke editor for creating, editing, and maintaining UltraStar and UltraStar Deluxe song files.  
It allows karaoke creators to precisely edit notes, timing, and lyrics, manage large song libraries, and fix common errors in existing karaoke tracks.

The application is written in Java and runs on Windows, macOS, and Linux.  
It is actively developed and based on modern Java (Java 21).

Yass Reloaded provides a graphical user interface for fine-tuning UltraStar karaoke songs.
You can drag and drop notes, adjust syllables, correct timing issues, and quickly detect common formatting errors.

In addition, Yass Reloaded offers powerful tools for managing karaoke libraries, including filtering, mass-tagging, and batch correction of song metadata.

## Who is this for?

Yass Reloaded is designed for:
- UltraStar karaoke song creators
- Karaoke enthusiasts who want to edit lyrics and timing precisely
- Users maintaining large UltraStar song libraries
- Advanced users who need batch editing and error detection

## Features of the UltraStar Karaoke Editor

*   **Graphical Note and Lyric Editing:** Easily drag and drop notes, adjust timing, and edit lyrics with a user-friendly interface.
*   **Advanced Song Management:** Filter your song library, batch-edit tags, and organize your collection efficiently.
*   **Error Detection:** Automatically finds common errors in your karaoke files to ensure they play perfectly.
*   **Wide Audio Format Support:** Thanks to FFmpeg integration, Yass Reloaded supports a vast range of audio formats, including AAC, OGG, and OPUS.
*   **Improved User Experience:** Numerous tweaks and improvements make the editing process faster and more intuitive.
*   **Modern and Actively Developed:** Built on Java 21 and continuously updated with new features and bug fixes.

## Supported Languages

Yass Reloaded has been localized to:
- English
- German
- French
- Spanish
- Polish
- Hungarian

French has just recently been added and been translated by AI. Please let me know, if you find any mistakes or missing translations.
If you want to help me localize Yass Reloaded to your language, please let me know as well.

## Project Background
Yass Reloaded is a fork of Yass 2.4.3, originally developed by Saruta since 2009.  
Due to limited maintenance and different development goals, this fork was created to modernize the codebase, improve usability, and actively incorporate new UltraStar format extensions.

## Differences Compared to the Original Yass
- Extended audio format support via FFmpeg (AAC, OGG, OPUS and more)
- Improved user experience focused on real-world karaoke song creation
- Faster bug fixing and active maintenance
- Support for new and extended UltraStar song format tags
- Modern Java 21 codebase instead of legacy Java 8

## Downloads

Prebuilt binaries for Windows, macOS, and Linux are available in the GitHub
[Release Section](https://github.com/DoubleDee73/Yass/releases)

Min. Requirement is Java 21 (e. g. Open JDK ZuluFX https://www.azul.com/downloads/).
While JavaFX is no longer required, it may be needed in future releases.

Yass Reloaded requires FFmpeg to support a variety of audio formats.
https://www.ffmpeg.org/download.html
- Windows (`winget install --id=Gyan.FFmpeg -e`) - Check out: https://www.gyan.dev/ffmpeg/builds/
- MacOS (`brew install ffmpeg`)
- Linux (`sudo apt install ffmpeg`)

Also, it is highly recommended to download Ultrastar Creator to get you started (https://github.com/UltraStar-Deluxe/UltraStar-Creator)

# Starting Yass Reloaded
If you install Yass Reloaded with the Windows installer, you can simply run `Yass.exe`

Alternatively, you can download the jar file and start it from the console:

On Windows, you can simply do this: `java -jar Yass-Reloaded-2025.11.jar`

On Mac/Linux, it is recommended, that you add a runtime variable like so:

`export PATH_TO_FX=path/to/javafx-sdk-[whateverversion]/lib`

and then start the jar like so:

`java --module-path $PATH_TO_FX --add-modules javafx.swing,javafx.media -jar Yass-Reloaded-2025.11.jar`

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

# Support for external tools
## yt-dlp
yt-dlp is used to download audio and video files from YouTube
https://github.com/yt-dlp/yt-dlp#release-files
## aubio
aubio is used to determine the BPM when using the Song Creation Wizard
https://aubio.org/download

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
  - Open a command line window and type in `java -version`. 
  - You should have Java 21
- Have you tried opening it as a jar?
  - Download the jar, open a command line window and type in `java -jar Yass-Reloaded-2025.11.jar`
  - Any error messages are then printed out in the command line window for debugging

## Cannot find FFmpeg?
Yass Reloaded has a built-in auto-detection for FFmpeg. 
It will look in any PATH environment variables and try to find both ffmpeg and ffprobe.  
If you are sure, you have installed ffmpeg, maybe you did not choose to have the PATH variable automatically configured.  
If this is the cae, then it is recommended, that you add the FFmpeg path to your PATH variables

You can also add the path to FFmpeg to the Yass Reloaded configuration file. The user.xml should be in the ~/.yass/ folder

There should be an entry:

`<entry key="ffmpegPath"></entry>`

If it's not, add it, and put the FFmpeg path before </entry>, e. g.:

`<entry key="ffmpegPath">/usr/bin</entry>`

or

`<entry key="ffmpegPath">C:\ffmpeg\bin</entry>`

depending on your OS.

It is also possible, that when you use Automator on Mac to start Yass Reloaded, 
that due to restrictions to the PATH variables, the auto-detection fails.

In this case, start Yass Reloaded from the console. If FFmpeg is then detected, the path
will be written into the user.xml, and you can use Automator again.
