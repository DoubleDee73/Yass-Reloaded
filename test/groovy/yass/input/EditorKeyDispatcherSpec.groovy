package yass.input

import spock.lang.Specification

import javax.swing.*
import java.awt.*
import java.awt.event.KeyEvent
import java.util.function.Supplier

class EditorKeyDispatcherSpec extends Specification {

    def cleanup() {
        MenuSelectionManager.defaultManager().clearSelectedPath()
        KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager())
    }

    def 'dispatches key released events for imported editor bindings'() {
        given:
        def focusOwner = new JPanel()
        KeyboardFocusManager.setCurrentKeyboardFocusManager(new StubKeyboardFocusManager(focusOwner))
        def registry = new EditorKeyBindingRegistry()
        def tracker = new KeySequenceTracker(500)
        def commandCalls = []
        registry.bind(javax.swing.KeyStroke.getKeyStroke('released UP'),
                new SimpleEditorCommand('prevPageReleased', { true }, { commandCalls << it.focusArea() }))
        Supplier<EditorInputContext> contextSupplier = {
            new EditorInputContext(FocusArea.SHEET, false, false, false, false, false, true, false)
        }
        def dispatcher = new EditorKeyDispatcher({ true }, contextSupplier, registry, tracker)
        def event = new KeyEvent(focusOwner, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED)

        when:
        def handled = dispatcher.dispatchKeyEvent(event)

        then:
        handled
        event.consumed
        commandCalls == [FocusArea.SHEET]
    }

    def 'dispatches key pressed events for normal editor bindings'() {
        given:
        def focusOwner = new JPanel()
        KeyboardFocusManager.setCurrentKeyboardFocusManager(new StubKeyboardFocusManager(focusOwner))
        def registry = new EditorKeyBindingRegistry()
        def tracker = new KeySequenceTracker(500)
        def commandCalls = []
        registry.bind(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
                new SimpleEditorCommand('prevPagePressed', { true }, { commandCalls << it.focusArea() }))
        Supplier<EditorInputContext> contextSupplier = {
            new EditorInputContext(FocusArea.SHEET, false, false, false, false, false, true, false)
        }
        def dispatcher = new EditorKeyDispatcher({ true }, contextSupplier, registry, tracker)
        def event = new KeyEvent(focusOwner, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED)

        when:
        def handled = dispatcher.dispatchKeyEvent(event)

        then:
        handled
        event.consumed
        commandCalls == [FocusArea.SHEET]
    }

    def 'dispatches down key released events for imported editor bindings'() {
        given:
        def focusOwner = new JPanel()
        KeyboardFocusManager.setCurrentKeyboardFocusManager(new StubKeyboardFocusManager(focusOwner))
        def registry = new EditorKeyBindingRegistry()
        def tracker = new KeySequenceTracker(500)
        def commandCalls = []
        registry.bind(javax.swing.KeyStroke.getKeyStroke('released DOWN'),
                new SimpleEditorCommand('nextPageReleased', { true }, { commandCalls << it.focusArea() }))
        Supplier<EditorInputContext> contextSupplier = {
            new EditorInputContext(FocusArea.SHEET, false, false, false, false, false, true, false)
        }
        def dispatcher = new EditorKeyDispatcher({ true }, contextSupplier, registry, tracker)
        def event = new KeyEvent(focusOwner, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED)

        when:
        def handled = dispatcher.dispatchKeyEvent(event)

        then:
        handled
        event.consumed
        commandCalls == [FocusArea.SHEET]
    }

    def 'does not dispatch up key when a swing menu is open'() {
        given:
        def focusOwner = new JPanel()
        KeyboardFocusManager.setCurrentKeyboardFocusManager(new StubKeyboardFocusManager(focusOwner))
        def popupMenu = new JPopupMenu()
        def menuItem = new JMenuItem('Entry')
        popupMenu.add(menuItem)
        MenuSelectionManager.defaultManager().setSelectedPath([popupMenu, menuItem] as javax.swing.MenuElement[])

        def registry = new EditorKeyBindingRegistry()
        def tracker = new KeySequenceTracker(500)
        def commandCalls = []
        registry.bind(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
                new SimpleEditorCommand('prevPagePressed', { true }, { commandCalls << it.focusArea() }))
        Supplier<EditorInputContext> contextSupplier = {
            new EditorInputContext(FocusArea.SHEET, false, false, false, false, false, true, false)
        }
        def dispatcher = new EditorKeyDispatcher({ true }, contextSupplier, registry, tracker)
        def event = new KeyEvent(focusOwner, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED)

        when:
        def handled = dispatcher.dispatchKeyEvent(event)

        then:
        !handled
        !event.consumed
        commandCalls.isEmpty()
    }

    def 'does not treat held shift-down repeats as multi-press escalation'() {
        given:
        def focusOwner = new JPanel()
        KeyboardFocusManager.setCurrentKeyboardFocusManager(new StubKeyboardFocusManager(focusOwner))
        def registry = new EditorKeyBindingRegistry()
        def tracker = new KeySequenceTracker(500)
        def commandCalls = []
        registry.bind(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.SHIFT_DOWN_MASK),
                new SimpleEditorCommand('shiftDownSingle', { true }, { commandCalls << 'single' }))
        registry.bind(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.SHIFT_DOWN_MASK), 2,
                new SimpleEditorCommand('shiftDownDouble', { true }, { commandCalls << 'double' }))
        Supplier<EditorInputContext> contextSupplier = {
            new EditorInputContext(FocusArea.SHEET, false, false, false, false, false, true, false)
        }
        def dispatcher = new EditorKeyDispatcher({ true }, contextSupplier, registry, tracker)

        when:
        def firstPress = new KeyEvent(focusOwner, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), KeyEvent.SHIFT_DOWN_MASK, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED)
        def repeatedPress = new KeyEvent(focusOwner, KeyEvent.KEY_PRESSED, System.currentTimeMillis() + 20, KeyEvent.SHIFT_DOWN_MASK, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED)
        dispatcher.dispatchKeyEvent(firstPress)
        dispatcher.dispatchKeyEvent(repeatedPress)

        then:
        commandCalls == ['single', 'single']
    }

    def 'counts shift-down as true double press only after release and second press'() {
        given:
        def focusOwner = new JPanel()
        KeyboardFocusManager.setCurrentKeyboardFocusManager(new StubKeyboardFocusManager(focusOwner))
        def registry = new EditorKeyBindingRegistry()
        def tracker = new KeySequenceTracker(500)
        def commandCalls = []
        registry.bind(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.SHIFT_DOWN_MASK),
                new SimpleEditorCommand('shiftDownSingle', { true }, { commandCalls << 'single' }))
        registry.bind(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.SHIFT_DOWN_MASK), 2,
                new SimpleEditorCommand('shiftDownDouble', { true }, { commandCalls << 'double' }))
        Supplier<EditorInputContext> contextSupplier = {
            new EditorInputContext(FocusArea.SHEET, false, false, false, false, false, true, false)
        }
        def dispatcher = new EditorKeyDispatcher({ true }, contextSupplier, registry, tracker)

        when:
        dispatcher.dispatchKeyEvent(new KeyEvent(focusOwner, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), KeyEvent.SHIFT_DOWN_MASK, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED))
        dispatcher.dispatchKeyEvent(new KeyEvent(focusOwner, KeyEvent.KEY_RELEASED, System.currentTimeMillis() + 10, KeyEvent.SHIFT_DOWN_MASK, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED))
        dispatcher.dispatchKeyEvent(new KeyEvent(focusOwner, KeyEvent.KEY_PRESSED, System.currentTimeMillis() + 20, KeyEvent.SHIFT_DOWN_MASK, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED))

        then:
        commandCalls == ['single', 'double']
    }

    def 'dispatches single shift-up selection after shift modifier is pressed first'() {
        given:
        def focusOwner = new JPanel()
        KeyboardFocusManager.setCurrentKeyboardFocusManager(new StubKeyboardFocusManager(focusOwner))
        def registry = new EditorKeyBindingRegistry()
        def tracker = new KeySequenceTracker(500)
        def commandCalls = []
        registry.bind(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.SHIFT_DOWN_MASK),
                new SimpleEditorCommand('selectPrevBeat', { true }, { commandCalls << 'single' }))
        registry.bind(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.SHIFT_DOWN_MASK), 2,
                new SimpleEditorCommand('selectCurrentWordUp', { true }, { commandCalls << 'double' }))
        registry.bind(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.SHIFT_DOWN_MASK), 3,
                new SimpleEditorCommand('selectToStartOfCurrentPage', { true }, { commandCalls << 'triple' }))
        Supplier<EditorInputContext> contextSupplier = {
            new EditorInputContext(FocusArea.SHEET, false, false, false, false, false, true, false)
        }
        def dispatcher = new EditorKeyDispatcher({ true }, contextSupplier, registry, tracker)

        when:
        def shiftPress = new KeyEvent(focusOwner, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), KeyEvent.SHIFT_DOWN_MASK, KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED)
        def upPress = new KeyEvent(focusOwner, KeyEvent.KEY_PRESSED, System.currentTimeMillis() + 10, KeyEvent.SHIFT_DOWN_MASK, KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED)
        def handledShift = dispatcher.dispatchKeyEvent(shiftPress)
        def handledUp = dispatcher.dispatchKeyEvent(upPress)

        then:
        !handledShift
        handledUp
        upPress.consumed
        commandCalls == ['single']
    }

    def 'dispatches character shortcuts by typed character in sheet and lyrics view'() {
        given:
        def focusOwner = new JPanel()
        KeyboardFocusManager.setCurrentKeyboardFocusManager(new StubKeyboardFocusManager(focusOwner))
        def registry = new EditorKeyBindingRegistry()
        def tracker = new KeySequenceTracker(500)
        def commandCalls = []
        registry.bind(shortcutChar as char,
                new SimpleEditorCommand(commandId, { true }, { commandCalls << it.focusArea() }))
        Supplier<EditorInputContext> contextSupplier = {
            new EditorInputContext(focusArea, false, false, false, false, false, true, false)
        }
        def dispatcher = new EditorKeyDispatcher({ true }, contextSupplier, registry, tracker)
        def event = new KeyEvent(focusOwner, KeyEvent.KEY_TYPED, System.currentTimeMillis(), modifiers, KeyEvent.VK_UNDEFINED, shortcutChar as char)

        when:
        def handled = dispatcher.dispatchKeyEvent(event)

        then:
        handled
        event.consumed
        commandCalls == [focusArea]

        where:
        focusArea             | modifiers                | shortcutChar | commandId
        FocusArea.SHEET       | 0                        | '+'          | 'joinRows'
        FocusArea.LYRICS_VIEW | KeyEvent.SHIFT_DOWN_MASK | '+'          | 'joinRows'
        FocusArea.SHEET       | KeyEvent.SHIFT_DOWN_MASK | '_'          | 'minus'
        FocusArea.LYRICS_VIEW | KeyEvent.SHIFT_DOWN_MASK | '_'          | 'minus'
        FocusArea.SHEET       | 0                        | '-'          | 'splitRows'
        FocusArea.LYRICS_VIEW | 0                        | '-'          | 'splitRows'
        FocusArea.SHEET       | KeyEvent.SHIFT_DOWN_MASK | '~'          | 'addEndian'
        FocusArea.LYRICS_VIEW | KeyEvent.SHIFT_DOWN_MASK | '~'          | 'addEndian'
    }

    def 'dispatches AltGr character shortcuts by typed character'() {
        given:
        def focusOwner = new JPanel()
        KeyboardFocusManager.setCurrentKeyboardFocusManager(new StubKeyboardFocusManager(focusOwner))
        def registry = new EditorKeyBindingRegistry()
        def tracker = new KeySequenceTracker(500)
        def commandCalls = []
        registry.bind('~' as char,
                new SimpleEditorCommand('addEndian', { true }, { commandCalls << 'addEndian' }))
        Supplier<EditorInputContext> contextSupplier = {
            new EditorInputContext(FocusArea.SHEET, false, false, false, false, false, true, false)
        }
        def dispatcher = new EditorKeyDispatcher({ true }, contextSupplier, registry, tracker)
        int altGrModifiers = KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK | KeyEvent.ALT_GRAPH_DOWN_MASK
        def event = new KeyEvent(focusOwner, KeyEvent.KEY_TYPED, System.currentTimeMillis(), altGrModifiers,
                KeyEvent.VK_UNDEFINED, '~' as char)

        when:
        def handled = dispatcher.dispatchKeyEvent(event)

        then:
        handled
        event.consumed
        commandCalls == ['addEndian']
    }

    def 'dispatches AltGr dead tilde through the dead key binding'() {
        given:
        def focusOwner = new JPanel()
        KeyboardFocusManager.setCurrentKeyboardFocusManager(new StubKeyboardFocusManager(focusOwner))
        def registry = new EditorKeyBindingRegistry()
        def tracker = new KeySequenceTracker(500)
        def commandCalls = []
        registry.bind(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_DEAD_TILDE, 0),
                new SimpleEditorCommand('addEndian', { true }, { commandCalls << 'addEndian' }))
        Supplier<EditorInputContext> contextSupplier = {
            new EditorInputContext(FocusArea.SHEET, false, false, false, false, false, true, false)
        }
        def dispatcher = new EditorKeyDispatcher({ true }, contextSupplier, registry, tracker)
        int altGrModifiers = KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK | KeyEvent.ALT_GRAPH_DOWN_MASK
        def event = new KeyEvent(focusOwner, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), altGrModifiers,
                KeyEvent.VK_DEAD_TILDE, KeyEvent.CHAR_UNDEFINED)

        when:
        def handled = dispatcher.dispatchKeyEvent(event)

        then:
        handled
        event.consumed
        commandCalls == ['addEndian']
    }

    def 'does not dispatch the typed tilde again after handling a dead tilde press'() {
        given:
        def focusOwner = new JPanel()
        KeyboardFocusManager.setCurrentKeyboardFocusManager(new StubKeyboardFocusManager(focusOwner))
        def registry = new EditorKeyBindingRegistry()
        def tracker = new KeySequenceTracker(500)
        def commandCalls = []
        registry.bind(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_DEAD_TILDE, 0),
                new SimpleEditorCommand('addEndian', { true }, { commandCalls << 'deadTilde' }))
        registry.bind('~' as char,
                new SimpleEditorCommand('addEndian', { true }, { commandCalls << 'typedTilde' }))
        Supplier<EditorInputContext> contextSupplier = {
            new EditorInputContext(FocusArea.SHEET, false, false, false, false, false, true, false)
        }
        def dispatcher = new EditorKeyDispatcher({ true }, contextSupplier, registry, tracker)
        int altGrModifiers = KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK | KeyEvent.ALT_GRAPH_DOWN_MASK
        def press = new KeyEvent(focusOwner, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), altGrModifiers,
                KeyEvent.VK_DEAD_TILDE, KeyEvent.CHAR_UNDEFINED)
        def typed = new KeyEvent(focusOwner, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,
                KeyEvent.VK_UNDEFINED, '~' as char)

        when:
        def pressHandled = dispatcher.dispatchKeyEvent(press)
        def typedHandled = dispatcher.dispatchKeyEvent(typed)

        then:
        pressHandled
        press.consumed
        typedHandled
        typed.consumed
        commandCalls == ['deadTilde']
    }

    def 'does not dispatch AltGr dead tilde while typing lyrics'() {
        given:
        def focusOwner = new JPanel()
        KeyboardFocusManager.setCurrentKeyboardFocusManager(new StubKeyboardFocusManager(focusOwner))
        def registry = new EditorKeyBindingRegistry()
        def tracker = new KeySequenceTracker(500)
        def commandCalls = []
        registry.bind(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_DEAD_TILDE, 0),
                new SimpleEditorCommand('addEndian', { true }, { commandCalls << 'addEndian' }))
        Supplier<EditorInputContext> contextSupplier = {
            new EditorInputContext(FocusArea.LYRICS_EDIT, true, false, false, false, false, true, false)
        }
        def dispatcher = new EditorKeyDispatcher({ true }, contextSupplier, registry, tracker)
        int altGrModifiers = KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK | KeyEvent.ALT_GRAPH_DOWN_MASK
        def event = new KeyEvent(focusOwner, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), altGrModifiers,
                KeyEvent.VK_DEAD_TILDE, KeyEvent.CHAR_UNDEFINED)

        when:
        def handled = dispatcher.dispatchKeyEvent(event)

        then:
        !handled
        !event.consumed
        commandCalls.isEmpty()
    }

    def 'does not dispatch character shortcuts while typing or outside editor content'() {
        given:
        def focusOwner = new JPanel()
        KeyboardFocusManager.setCurrentKeyboardFocusManager(new StubKeyboardFocusManager(focusOwner))
        def registry = new EditorKeyBindingRegistry()
        def tracker = new KeySequenceTracker(500)
        def commandCalls = []
        registry.bind('+' as char,
                new SimpleEditorCommand('joinRows', { true }, { commandCalls << it.focusArea() }))
        Supplier<EditorInputContext> contextSupplier = {
            new EditorInputContext(focusArea, false, songHeaderEditing, false, false, false, true, false)
        }
        def dispatcher = new EditorKeyDispatcher({ true }, contextSupplier, registry, tracker)
        def event = new KeyEvent(focusOwner, KeyEvent.KEY_TYPED, System.currentTimeMillis(), modifiers, KeyEvent.VK_UNDEFINED, '+' as char)

        when:
        def handled = dispatcher.dispatchKeyEvent(event)

        then:
        !handled
        !event.consumed
        commandCalls.isEmpty()

        where:
        focusArea             | songHeaderEditing | modifiers
        FocusArea.LYRICS_EDIT | false             | 0
        FocusArea.SONG_HEADER | false             | 0
        FocusArea.OTHER       | false             | 0
        FocusArea.SHEET       | true              | 0
        FocusArea.SHEET       | false             | KeyEvent.CTRL_DOWN_MASK
    }

    private static class StubKeyboardFocusManager extends DefaultKeyboardFocusManager {
        private final java.awt.Component focusOwner

        StubKeyboardFocusManager(java.awt.Component focusOwner) {
            this.focusOwner = focusOwner
        }

        @Override
        java.awt.Component getFocusOwner() {
            return focusOwner
        }
    }
}
