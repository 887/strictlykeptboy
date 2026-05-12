package com.eight87.strictlykeptboy.store

import org.junit.Assert.assertEquals
import org.junit.Test

class FrontmatterTest {

    @Test fun splitFencesAndBody() {
        val text = """
            +++
            id = "abc"
            +++

            hello body
        """.trimIndent() + "\n"
        val doc = FrontmatterReader.parse(text)
        assertEquals(FrontmatterDoc.Kind.Ok, doc.kind)
        assertEquals("abc", doc.frontmatter.getString("id"))
        assertEquals("\nhello body\n", doc.body)
    }

    @Test fun noFrontmatterTolerated() {
        val text = "just a markdown note\n"
        val doc = FrontmatterReader.parse(text)
        assertEquals(FrontmatterDoc.Kind.NoFrontmatter, doc.kind)
        assertEquals(text, doc.body)
    }

    @Test fun missingClosingFenceIsMalformed() {
        val text = "+++\nid = \"x\"\nno close here\n"
        val doc = FrontmatterReader.parse(text)
        assertEquals(FrontmatterDoc.Kind.MalformedFrontmatter, doc.kind)
    }

    @Test fun roundTripPreservesScalars() {
        val text = """
            +++
            id = "01900000-0000-7000-8000-aaaaaaaaaaaa"
            kind = "event"
            tags = ["x", "y"]
            +++
            body line
        """.trimIndent() + "\n"
        val doc = FrontmatterReader.parse(text)
        val out = FrontmatterWriter.serialize(doc)
        val reparsed = FrontmatterReader.parse(out)
        assertEquals(doc.frontmatter.getString("id"), reparsed.frontmatter.getString("id"))
        assertEquals(doc.frontmatter.getStringArray("tags"), reparsed.frontmatter.getStringArray("tags"))
        assertEquals(doc.body.trimEnd(), reparsed.body.trimEnd())
    }
}
