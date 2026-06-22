package com.shinjikai.dictionary.data

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OfflineImageSupportTest {
    @Test
    fun `canonical picture normalization unwraps nested bookmark image objects`() {
        val details = WordDetailsResponse(
            word = WordDetailsWord(
                id = 1,
                meanings = listOf(
                    Meaning(
                        arabic = "test",
                        pictures = listOf(
                            JsonParser.parseString("""{"Image":{"Filename":"cat.png"}}"""),
                            JsonParser.parseString("""{"media":[{"url":"gallery/dog.webp"}]}""")
                        )
                    )
                )
            )
        )

        val pictures = details.withCanonicalPictureElements()
            .word
            .meanings
            .single()
            .pictures
            .map { it.asString }

        assertEquals(listOf("cat.png", "gallery/dog.webp"), pictures)
    }

    @Test
    fun `resolved offline images keep absolute file paths for bookmarked details`() {
        val imageRoot = File("build/test-offline/yomitan_images").absolutePath.replace('\\', '/')
        val details = WordDetailsResponse(
            word = WordDetailsWord(
                id = 2,
                meanings = listOf(
                    Meaning(
                        arabic = "test",
                        pictures = listOf(
                            JsonParser.parseString("""{"Picture":{"Path":"animals/cat.jpg"}}"""),
                            JsonParser.parseString(""""already.png"""")
                        )
                    )
                )
            )
        )

        val pictures = details.withResolvedOfflineImages(imageRoot)
            .word
            .meanings
            .single()
            .pictures
            .map { it.asString }

        assertEquals("$imageRoot/animals/cat.jpg", pictures[0])
        assertEquals("$imageRoot/already.png", pictures[1])
    }

    @Test
    fun `picture reference extractor scans nested arrays and fallback keys`() {
        val picture = JsonParser.parseString(
            """{"payload":{"items":[{"href":"https://example.com/image.jpg"}]}}"""
        )

        val extracted = extractPictureReference(picture)

        assertEquals("https://example.com/image.jpg", extracted)
        assertTrue(extracted!!.startsWith("https://"))
    }
}
