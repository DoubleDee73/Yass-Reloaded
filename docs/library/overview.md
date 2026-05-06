---
title: Library Overview
---

# Library Overview

The library is where Yass Reloaded helps you organize, inspect, and maintain large song collections.

## What the Library Is Good At

Typical library workflows include:

- opening songs for editing
- sorting and filtering by metadata
- checking errors across many songs
- batch-fixing common metadata issues
- managing covers and related media

## Common Tasks

### Find and Open Songs

Useful actions include:

- `Ctrl-F` to search
- `Ctrl-O` to open the selected song in the editor
- `Ctrl-Enter` to open the song folder

### Sort and Compare

You can:

- sort by visible columns in details view
- group by metadata such as language or folder
- compare several similar songs before editing

### Batch Maintenance

The library is often the fastest place to fix recurring issues:

- correct misspelled languages
- normalize medley-related values
- set folder-based metadata like edition
- review errors before opening individual songs

### Cover and Media Work

From the library side, you can also:

- inspect song media
- trigger fanart.tv-based cover lookup
- update metadata after saving new assets

### fanart.tv Cover Picker

The fanart.tv dialog is meant for browsing multiple cover candidates without downloading one immediately by accident.

Important behavior:

- typing in the search field and pressing `Enter` jumps to the next matching album tile
- the jumped-to tile is also selected visually
- pressing `Enter` again continues to the next match and wraps around at the end
- the actual cover is only taken when you explicitly confirm the download action

The dialog also prefers smoother browsing over aggressive bulk loading:

- cover previews are loaded lazily
- only visible candidates are fetched first
- if a preview host is unavailable, Yass Reloaded can fall back to the original fanart image URL

## USDB Workflows

Yass Reloaded can integrate directly with USDB from the library and from the song editor.

### Search USDB

If a USDB user name is configured, the library offers `Search USDB`.

Typical workflow:

1. open the USDB search dialog from the library
2. search with a single term that matches artist or title
3. review the result markers before importing

The match marker column helps you understand the current state quickly:

- empty: no local match was found
- yellow dot: a local song with matching artist and title exists
- green dot: a local song exists and is already linked through a `.usdb` file
- red dot: the song is already queued for import

If a USDB Syncer path is configured, Yass Reloaded uses the local Syncer database first and only falls back to the live USDB website when needed.

### Import Songs from USDB

Importing from the search dialog adds songs to the USDB import queue instead of blocking the main UI.

The queue dialog shows:

- the overall queue status
- one row per song
- a detail log for the selected song
- download, image, finalize, and optional separation progress

After a successful import, Yass Reloaded refreshes only the affected library entry instead of rescanning the entire library.

### Compare with USDB

If a song already has a `.usdb` file with a valid USDB song id, or if Yass can identify a matching USDB song, you can use `Compare with USDB`.

This action is available:

- from the library
- from the song editor

The diff dialog is designed for careful review before syncing changes:

- current USDB TXT on the left
- editable local version on the right
- next/previous difference navigation
- stronger highlighting for changed metadata and note fields
- `>>` to copy the current change from USDB to the local side
- `Save Locally` to merge reviewed timing or note updates back into the local song
- `Save To USDB` to submit the reviewed version back to USDB

If no `.usdb` file exists yet, saving locally or saving to USDB creates one automatically so the relationship is preserved for future comparisons.

### Direct Editing Permissions

Submitting changes back to USDB is only available when the current USDB login has direct editing rights.

Yass Reloaded distinguishes between:

- regular user access
- direct edit access for moderator/admin-style accounts

If direct editing is not available, you can still use the search and comparison workflow, but not the final direct submit step.

## Error-Driven Editing

One of the strongest workflows in Yass Reloaded is:

1. find issues in the library or error views
2. jump directly to the affected song or note
3. fix the issue in the editor
4. return to the library for the next item

## Related Pages

- [Quick Start](../quick-start.md)
- [External Tools](../tools/overview.md)
- [Troubleshooting](../reference/troubleshooting.md)
