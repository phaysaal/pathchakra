package com.seenslide.teacher.core.drawing

/**
 * Manages the drawing state: elements on canvas, undo/redo stack, current tool settings.
 */
class DrawingState {
    private val _elements = mutableListOf<DrawElement>()
    val elements: List<DrawElement> get() = _elements

    private val undoStack = mutableListOf<DrawElement>()
    private val redoStack = mutableListOf<DrawElement>()

    var currentTool: DrawTool = DrawTool.PEN
    var currentColor: Long = 0xFF000000 // black
    var currentWidth: Float = 3f

    // Listeners for live sync
    var onElementAdded: ((DrawElement) -> Unit)? = null
    var onElementRemoved: ((String) -> Unit)? = null

    fun addElement(element: DrawElement) {
        _elements.add(element)
        undoStack.add(element)
        redoStack.clear()
        onElementAdded?.invoke(element)
    }

    fun undo(): DrawElement? {
        if (undoStack.isEmpty()) return null
        val element = undoStack.removeAt(undoStack.size - 1)
        _elements.removeAll { it.id == element.id }
        redoStack.add(element)
        onElementRemoved?.invoke(element.id)
        return element
    }

    fun redo(): DrawElement? {
        if (redoStack.isEmpty()) return null
        val element = redoStack.removeAt(redoStack.size - 1)
        _elements.add(element)
        undoStack.add(element)
        onElementAdded?.invoke(element)
        return element
    }

    fun eraseAt(x: Float, y: Float, radius: Float = 0.02f): List<String> {
        val erased = mutableListOf<String>()
        val toRemove = _elements.filter { element ->
            when (element) {
                is DrawElement.FreehandElement ->
                    element.stroke.points.any { p ->
                        val dx = p.x - x
                        val dy = p.y - y
                        dx * dx + dy * dy < radius * radius
                    }
                is DrawElement.ShapeElement -> {
                    val s = element.shape
                    isPointNearRect(x, y, s.startX, s.startY, s.endX, s.endY, radius)
                }
                is DrawElement.TextElement -> {
                    val t = element.textStroke
                    val dx = t.x - x
                    val dy = t.y - y
                    dx * dx + dy * dy < radius * radius * 4 // larger hit area for text
                }
            }
        }
        for (el in toRemove) {
            _elements.remove(el)
            undoStack.add(el) // store for undo
            erased.add(el.id)
            onElementRemoved?.invoke(el.id)
        }
        return erased
    }

    fun clear() {
        _elements.clear()
        undoStack.clear()
        redoStack.clear()
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    private fun isPointNearRect(
        px: Float, py: Float,
        x1: Float, y1: Float, x2: Float, y2: Float,
        radius: Float,
    ): Boolean {
        val minX = minOf(x1, x2) - radius
        val maxX = maxOf(x1, x2) + radius
        val minY = minOf(y1, y2) - radius
        val maxY = maxOf(y1, y2) + radius
        return px in minX..maxX && py in minY..maxY
    }
}
