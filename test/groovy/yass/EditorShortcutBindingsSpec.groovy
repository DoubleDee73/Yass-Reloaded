package yass

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class EditorShortcutBindingsSpec extends Specification {

    def 'join rows uses a character shortcut for visible plus across keyboard layouts'() {
        given:
        String actionsSource = Files.readString(Path.of('src/yass/YassActions.java'))

        expect:
        actionsSource.contains('bindEditorCharShortcut(\'+\', "joinRows"')
        !actionsSource.contains('KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.SHIFT_DOWN_MASK), "joinRows"')
    }

    def 'minus uses a character shortcut for visible underscore across keyboard layouts'() {
        given:
        String actionsSource = Files.readString(Path.of('src/yass/YassActions.java'))

        expect:
        actionsSource.contains('bindEditorCharShortcut(\'_\', "minus"')
        !actionsSource.contains('KeyStroke.getKeyStroke(KeyEvent.VK_UNDERSCORE, InputEvent.SHIFT_DOWN_MASK), "minus"')
    }

    def 'symbol editor actions use character shortcuts for visible symbols across keyboard layouts'() {
        given:
        String actionsSource = Files.readString(Path.of('src/yass/YassActions.java'))

        expect:
        actionsSource.contains('bindEditorCharShortcut(\'-\', "splitRows"')
        actionsSource.contains('bindEditorCharShortcut(\'~\', "addEndian"')
        !actionsSource.contains('KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), "splitRows"')
        !actionsSource.contains('KeyStroke.getKeyStroke("~"), "addEndian"')
    }

    def 'create duet stays in editor file menu and not editor edit menu'() {
        given:
        String actionsSource = Files.readString(Path.of('src/yass/YassActions.java'))
        String fileMenuBlock = sourceBetween(actionsSource,
                'menu = new JMenu(I18.get("edit_file"));',
                'menu = new JMenu(I18.get("edit_edit"));')
        String editMenuBlock = sourceBetween(actionsSource,
                'menu = new JMenu(I18.get("edit_edit"));',
                'menu = new JMenu(I18.get("edit_play"));')

        expect:
        fileMenuBlock.contains('menu.add(createDuet);')
        !editMenuBlock.contains('menu.add(createDuet);')
    }

    def 'align to melody variants are available from edit menu and keyboard shortcuts'() {
        given:
        String actionsSource = Files.readString(Path.of('src/yass/YassActions.java'))
        String editMenuBlock = sourceBetween(actionsSource,
                'menu = new JMenu(I18.get("edit_edit"));',
                'menu = new JMenu(I18.get("edit_play"));')

        expect:
        editMenuBlock.contains('menu.add(alignToMelody);')
        editMenuBlock.contains('menu.add(alignNoteLength);')
        editMenuBlock.contains('menu.add(alignPitch);')
        actionsSource.contains('KeyStroke.getKeyStroke(KeyEvent.VK_M, 0), "alignToMelody"')
        actionsSource.contains('KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK), "alignNoteLength"')
        actionsSource.contains('KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.SHIFT_DOWN_MASK), "alignPitch"')
    }

    def 'ctrl shift left and right remain aliases for shifting selected notes'() {
        given:
        String actionsSource = Files.readString(Path.of('src/yass/YassActions.java'))

        expect:
        actionsSource =~ /KeyStroke\.getKeyStroke\(KeyEvent\.VK_LEFT,\s*InputEvent\.CTRL_DOWN_MASK \| InputEvent\.SHIFT_DOWN_MASK\),\s*"shiftLeft"/
        actionsSource =~ /KeyStroke\.getKeyStroke\(KeyEvent\.VK_RIGHT,\s*InputEvent\.CTRL_DOWN_MASK \| InputEvent\.SHIFT_DOWN_MASK\),\s*"shiftRight"/
    }

    def 'ctrl enter routes to vocal-aware insert while shift enter remains legacy insert'() {
        given:
        String actionsSource = Files.readString(Path.of('src/yass/YassActions.java'))

        expect:
        actionsSource.contains('KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "insertNoteWithVocalPitch"')
        actionsSource.contains('KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "insertNote", insertNote')
    }

    def 'vocal-aware insert falls back to legacy insert unless vocals and pitch data are active'() {
        given:
        String actionsSource = Files.readString(Path.of('src/yass/YassActions.java'))
        String eligibilityBlock = sourceBetween(actionsSource,
                'private boolean shouldUseVocalAwareInsertNote() {',
                'private List<PitchDetector.PitchData> currentPitchDataForEditorAlignment()')
        String actionBlock = sourceBetween(actionsSource,
                'private final Action insertNoteWithVocalPitch = new AbstractAction(I18.get("edit_add")) {',
                'private final Action removeRows = new AbstractAction')

        expect:
        eligibilityBlock.contains('UltrastarHeaderTag.VOCALS.toString().equals(header.getSelectedAudio())')
        eligibilityBlock.contains('mp3.getPitchDataList() != null')
        eligibilityBlock.contains('!mp3.getPitchDataList().isEmpty()')
        actionBlock.contains('if (shouldUseVocalAwareInsertNote())')
        actionBlock.contains('table.insertNoteWithVocalPitch(currentPitchDataForEditorAlignment());')
        actionBlock =~ /\}\s+else\s+\{\s+table\.insertNote\(\);/
    }

    def 'insert note dialog uses ok as default and cancel or escape as non-mutating paths'() {
        given:
        String tableSource = Files.readString(Path.of('src/yass/YassTable.java'))
        String promptBlock = sourceBetween(tableSource,
                'private String promptForInsertedLyrics() {',
                'private int resolveInsertCursorBeat()')

        expect:
        promptBlock.contains('new JDialog(owner, I18.get("edit_insert_notes_title"), Dialog.ModalityType.APPLICATION_MODAL)')
        promptBlock.contains('new JLabel(I18.get("edit_insert_notes_prompt"))')
        promptBlock.contains('dialog.getRootPane().setDefaultButton(okButton);')
        promptBlock.contains('cancelButton.addActionListener(e -> {')
        promptBlock.contains('result[0] = null;')
        promptBlock.contains('KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)')
        promptBlock.contains('textField.requestFocusInWindow();')
        promptBlock.contains('textField.selectAll();')
    }

    def 'align timing and pitch labels are localized in every supported language'() {
        expect:
        localeLabels.every { fileName, expected ->
            String source = Files.readString(Path.of('src/yass/resources/i18', fileName))
            expected.every { key, label -> source.contains("${key} = ${label}") }
        }

        where:
        localeLabels = [
                'yass_de.properties': [
                        edit_align_note_length: 'Timing ausrichten',
                        edit_align_pitch      : 'Tonhöhe ausrichten'
                ],
                'yass_en.properties': [
                        edit_align_note_length: 'Align Timing',
                        edit_align_pitch      : 'Align Pitch'
                ],
                'yass_es.properties': [
                        edit_align_note_length: 'Alinear tiempos',
                        edit_align_pitch      : 'Alinear tono'
                ],
                'yass_fr.properties': [
                        edit_align_note_length: 'Aligner le timing',
                        edit_align_pitch      : 'Aligner la hauteur'
                ],
                'yass_hu.properties': [
                        edit_align_note_length: 'Időzítés igazítása',
                        edit_align_pitch      : 'Hangmagasság igazítása'
                ],
                'yass_pl.properties': [
                        edit_align_note_length: 'Dopasuj timing',
                        edit_align_pitch      : 'Dopasuj wysokość dźwięku'
                ]
        ]
    }

    private static String sourceBetween(String source, String start, String end) {
        int startIndex = source.indexOf(start)
        int endIndex = source.indexOf(end, startIndex)
        assert startIndex >= 0
        assert endIndex > startIndex
        source.substring(startIndex, endIndex)
    }
}
