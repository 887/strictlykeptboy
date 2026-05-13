package com.eight87.skb.cli

import com.eight87.skb.cli.help.HelpCatalog
import com.eight87.skb.cli.help.HelpRender
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpTest {
  @Test fun catalogHasCoreCommands() {
    val names = HelpCatalog.commands.map { it.name }.toSet()
    assertTrue(names.containsAll(listOf("event.add", "event.list", "event.show", "task.add", "cal.add", "repo.init", "help")))
  }

  @Test fun jsonCatalogIsValidEnvelope() {
    val raw = HelpRender.renderCatalogJson("0.1.0", "abc1234")
    assertTrue(raw.contains("\"command\":\"help\""))
    assertTrue(raw.contains("\"event.add\""))
    assertTrue(raw.contains("\"exit_codes\""))
  }

  @Test fun humanRenderShowsSynopsisAndFlags() {
    val c = HelpCatalog.findByName("event.add")
    assertNotNull(c)
    val text = HelpRender.renderCommandHuman(c!!)
    assertTrue(text.contains("--title"))
    assertTrue(text.contains("exit codes:"))
  }
}
