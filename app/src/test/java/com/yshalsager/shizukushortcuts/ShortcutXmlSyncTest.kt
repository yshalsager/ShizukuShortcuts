package com.yshalsager.shizukushortcuts

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ShortcutXmlSyncTest {
    @Test
    fun `static shortcuts stay in sync with registry`() {
        val shortcut_actions = mutableMapOf<String, String>()
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File("src/main/res/xml/shortcuts.xml"))
        val shortcuts = document.getElementsByTagName("shortcut")

        for (index in 0 until shortcuts.length) {
            val shortcut = shortcuts.item(index)
            val attributes = shortcut.attributes
            val shortcut_id = attributes.getNamedItem("android:shortcutId").nodeValue
            val intent = shortcut.childNodes
                .let { nodes -> (0 until nodes.length).map(nodes::item) }
                .first { it.nodeName == "intent" }
            val action = intent.attributes.getNamedItem("android:action").nodeValue
            shortcut_actions[shortcut_id] = action
        }

        assertEquals(ShortcutActions.ids.toSet(), shortcut_actions.keys)
        assertEquals(ShortcutActions.all.associate { it.id to it.shortcut_intent_action }, shortcut_actions)
    }
}
