package com.seenslide.teacher.feature.slide.maker

import com.seenslide.teacher.core.slidedoc.ShapeKind
import com.seenslide.teacher.core.slidedoc.SlideBackground
import com.seenslide.teacher.core.slidedoc.SlideDoc
import com.seenslide.teacher.core.slidedoc.SlideElement
import com.seenslide.teacher.core.slidedoc.SlideElementType

/**
 * Predefined starting points for common slide layouts. Each template returns
 * a fresh [SlideDoc] so element ids don't collide across applications.
 */
data class SlideTemplate(
    val key: String,
    val label: String,
    val build: () -> SlideDoc,
)

object SlideTemplates {

    val all: List<SlideTemplate> = listOf(
        SlideTemplate("blank", "Blank") {
            SlideDoc()
        },
        SlideTemplate("title", "Title") {
            SlideDoc(
                background = SlideBackground(type = "color", color = "#FFFFFF"),
                elements = listOf(
                    SlideElement(
                        type = SlideElementType.TEXT,
                        x = 0.08f, y = 0.38f, w = 0.84f, h = 0.18f,
                        content = "Your title",
                        fontSize = 0.12f,
                        color = "#212121",
                        align = "center",
                        bold = true,
                        z = 1,
                    ),
                    SlideElement(
                        type = SlideElementType.TEXT,
                        x = 0.12f, y = 0.60f, w = 0.76f, h = 0.10f,
                        content = "Subtitle",
                        fontSize = 0.06f,
                        color = "#616161",
                        align = "center",
                        z = 2,
                    ),
                ),
            )
        },
        SlideTemplate("title_content", "Title + content") {
            SlideDoc(
                elements = listOf(
                    SlideElement(
                        type = SlideElementType.TEXT,
                        x = 0.06f, y = 0.06f, w = 0.88f, h = 0.14f,
                        content = "Section title",
                        fontSize = 0.09f,
                        color = "#1A237E",
                        align = "left",
                        bold = true,
                        z = 1,
                    ),
                    SlideElement(
                        type = SlideElementType.SHAPE,
                        x = 0.06f, y = 0.21f, w = 0.30f, h = 0.006f,
                        kind = ShapeKind.RECT,
                        fill = "#1A237E",
                        z = 2,
                    ),
                    SlideElement(
                        type = SlideElementType.TEXT,
                        x = 0.06f, y = 0.26f, w = 0.88f, h = 0.64f,
                        content = "• Point one\n• Point two\n• Point three",
                        fontSize = 0.06f,
                        color = "#212121",
                        align = "left",
                        z = 3,
                    ),
                ),
            )
        },
        SlideTemplate("two_column", "Two columns") {
            SlideDoc(
                elements = listOf(
                    SlideElement(
                        type = SlideElementType.TEXT,
                        x = 0.06f, y = 0.06f, w = 0.88f, h = 0.12f,
                        content = "Compare",
                        fontSize = 0.08f,
                        color = "#212121",
                        align = "center",
                        bold = true,
                        z = 1,
                    ),
                    SlideElement(
                        type = SlideElementType.TEXT,
                        x = 0.06f, y = 0.22f, w = 0.42f, h = 0.08f,
                        content = "Left",
                        fontSize = 0.06f,
                        color = "#0D47A1",
                        align = "center",
                        bold = true,
                        z = 2,
                    ),
                    SlideElement(
                        type = SlideElementType.TEXT,
                        x = 0.52f, y = 0.22f, w = 0.42f, h = 0.08f,
                        content = "Right",
                        fontSize = 0.06f,
                        color = "#B71C1C",
                        align = "center",
                        bold = true,
                        z = 3,
                    ),
                    SlideElement(
                        type = SlideElementType.TEXT,
                        x = 0.06f, y = 0.32f, w = 0.42f, h = 0.58f,
                        content = "• ...\n• ...",
                        fontSize = 0.05f,
                        color = "#212121",
                        align = "left",
                        z = 4,
                    ),
                    SlideElement(
                        type = SlideElementType.TEXT,
                        x = 0.52f, y = 0.32f, w = 0.42f, h = 0.58f,
                        content = "• ...\n• ...",
                        fontSize = 0.05f,
                        color = "#212121",
                        align = "left",
                        z = 5,
                    ),
                ),
            )
        },
        SlideTemplate("image_caption", "Image + caption") {
            SlideDoc(
                elements = listOf(
                    SlideElement(
                        type = SlideElementType.SHAPE,
                        x = 0.20f, y = 0.10f, w = 0.60f, h = 0.60f,
                        kind = ShapeKind.RECT,
                        fill = "#ECEFF1",
                        stroke = "#90A4AE",
                        strokeWidth = 0.003f,
                        z = 1,
                    ),
                    SlideElement(
                        type = SlideElementType.TEXT,
                        x = 0.20f, y = 0.34f, w = 0.60f, h = 0.08f,
                        content = "(Add an image)",
                        fontSize = 0.045f,
                        color = "#607D8B",
                        align = "center",
                        italic = true,
                        z = 2,
                    ),
                    SlideElement(
                        type = SlideElementType.TEXT,
                        x = 0.10f, y = 0.76f, w = 0.80f, h = 0.12f,
                        content = "Caption goes here",
                        fontSize = 0.05f,
                        color = "#212121",
                        align = "center",
                        z = 3,
                    ),
                ),
            )
        },
        SlideTemplate("quote", "Quote") {
            SlideDoc(
                background = SlideBackground(type = "color", color = "#212121"),
                elements = listOf(
                    SlideElement(
                        type = SlideElementType.TEXT,
                        x = 0.10f, y = 0.30f, w = 0.80f, h = 0.30f,
                        content = "\u201CInsert memorable quote.\u201D",
                        fontSize = 0.08f,
                        color = "#FFFFFF",
                        align = "center",
                        italic = true,
                        z = 1,
                    ),
                    SlideElement(
                        type = SlideElementType.TEXT,
                        x = 0.10f, y = 0.68f, w = 0.80f, h = 0.08f,
                        content = "— Source",
                        fontSize = 0.05f,
                        color = "#BDBDBD",
                        align = "center",
                        z = 2,
                    ),
                ),
            )
        },
    )
}
