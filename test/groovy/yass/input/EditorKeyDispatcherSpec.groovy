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

    def 'dispatches character shortcuts by typed character in sheet and lyrics view'() {
        given:
        def focusOwner = new JPanel()
        KeyboardFocusManager.setCurrentKeyboardFocusManager(new StubKeyboardFocusManager(focusOwner))
        def registry = new EditorKeyBindingRegistry()
        def tracker = new KeySequenceTracker(500)
        def commandCalls = []
        registry.bind('+' as char,
                new SimpleEditorCommand('joinRows', { true }, { commandCalls << it.focusArea() }))
        Supplier<EditorInputContext> contextSupplier = {
            new EditorInputContext(focusArea, false, false, false, false, false, true, false)
        }
        def dispatcher = new EditorKeyDispatcher({ true }, contextSupplier, registry, tracker)
        def event = new KeyEvent(focusOwner, KeyEvent.KEY_TYPED, System.currentTimeMillis(), modifiers, KeyEvent.VK_UNDEFINED, '+' as char)

        when:
        def handled = dispatcher.dispatchKeyEvent(event)

        then:
        handled
        event.consumed
        commandCalls == [focusArea]

        where:
        focusArea             | modifiers
        FocusArea.SHEET       | 0
        FocusArea.LYRICS_VIEW | KeyEvent.SHIFT_DOWN_MASK
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
