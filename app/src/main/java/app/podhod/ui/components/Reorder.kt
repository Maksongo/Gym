package app.podhod.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Перетаскивание элементов LazyColumn за «ручку». Элементы идентифицируются ключами, поэтому
 * состояние полей внутри карточек не теряется при перестановке.
 *
 * @param headerCount сколько элементов LazyColumn стоит ПЕРЕД перетаскиваемым списком
 * @param itemCount текущее число элементов перетаскиваемого списка
 * @param onMove вызывается с индексами внутри перетаскиваемого списка (без учёта заголовков)
 */
@Stable
class ReorderState(
    private val list: LazyListState,
    private val headerCount: Int,
    private val itemCount: () -> Int,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    var draggingKey: Any? by mutableStateOf(null)
        private set

    /** Смещение (px) перетаскиваемой карточки относительно её текущего места. */
    var dragOffset: Float by mutableFloatStateOf(0f)
        private set

    private var expectedIndex = -1
    private var staleCount = 0

    fun start(key: Any) {
        draggingKey = key
        dragOffset = 0f
        expectedIndex = -1
        staleCount = 0
    }

    fun end() {
        draggingKey = null
        dragOffset = 0f
        expectedIndex = -1
    }

    fun drag(dy: Float) {
        val key = draggingKey ?: return
        dragOffset += dy
        val visible = list.layoutInfo.visibleItemsInfo
        val cur = visible.firstOrNull { it.key == key } ?: return

        // После перестановки раскладка обновляется не мгновенно: пока индекс «старый», новых перестановок не делаем.
        if (expectedIndex >= 0 && cur.index != expectedIndex) {
            if (++staleCount < 8) return
        }
        expectedIndex = -1
        staleCount = 0

        val dataIndex = cur.index - headerCount
        val center = cur.offset + cur.size / 2f + dragOffset
        val below = visible.firstOrNull { it.index == cur.index + 1 }
        val above = visible.firstOrNull { it.index == cur.index - 1 }

        if (below != null && dataIndex + 1 < itemCount() && center > below.offset + below.size / 2f) {
            onMove(dataIndex, dataIndex + 1)
            dragOffset -= ((below.offset + below.size) - (cur.offset + cur.size)).toFloat()
            expectedIndex = cur.index + 1
        } else if (above != null && dataIndex > 0 && center < above.offset + above.size / 2f) {
            onMove(dataIndex, dataIndex - 1)
            dragOffset -= (above.offset - cur.offset).toFloat()
            expectedIndex = cur.index - 1
        }

        // Автопрокрутка у краёв экрана.
        val info = list.layoutInfo
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        val edge = viewport * 0.12f
        val top = cur.offset + dragOffset
        val bottom = top + cur.size
        val delta = when {
            top < info.viewportStartOffset + edge -> -24f
            bottom > info.viewportEndOffset - edge -> 24f
            else -> 0f
        }
        if (delta != 0f) {
            val consumed = list.dispatchRawDelta(delta)
            dragOffset += consumed
        }
    }
}

/** Жест перетаскивания для «ручки» элемента с ключом [key]. */
fun Modifier.reorderHandle(state: ReorderState, key: Any): Modifier =
    pointerInput(key) {
        detectDragGestures(
            onDragStart = { state.start(key) },
            onDrag = { change, amount ->
                change.consume()
                state.drag(amount.y)
            },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() },
        )
    }
