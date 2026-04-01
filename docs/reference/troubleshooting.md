---
title: Troubleshooting
---

# Troubleshooting

## Yass Reloaded does not start correctly

Check:

- Java version if you use the JAR manually
- whether the packaged release for your platform is being used
- command-line output when starting from a terminal

## FFmpeg is not found

Check:

- PATH environment variable
- `External Tools -> Locations`
- your `user.xml`

## WhisperX or OpenAI alignment is not available

Check:

- API key configuration
- Python environment
- WhisperX installation
- FFmpeg availability

## fanart.tv cover search finds nothing

Check:

- the fanart.tv API key in `Library -> Setup`
- whether MusicBrainz could identify the artist / release context
- whether matching cover art exists on fanart.tv

## Pitch lines or alignment look wrong

Possible reasons:

- wrong selected audio source
- no `#VOCALS` track available
- octave interpretation differs between workflows
- note alignment has not yet been refined manually

## Recording does not apply notes as expected

Check:

- whether taps were fully recorded
- whether the intended note range was selected
- whether playback reached the end naturally or was interrupted
- whether post-alignment changed the visible octave

## The GitHub Pages documentation looks outdated

Check:

- whether the latest changes were pushed to the release branch
- whether the `Publish Documentation` GitHub Actions workflow succeeded
- whether GitHub Pages is configured to use `GitHub Actions`
