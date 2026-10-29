package app.podhod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.podhod.data.db.SetEntity
import app.podhod.domain.InputRules
import app.podhod.domain.LoadType
import app.podhod.domain.WeightUnit

/** Крупная круглая отметка «подход выполнен». Нажатие сразу переключает состояние. */
@Composable
fun CheckButton(
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (checked) primary else Color.Transparent)
            .border(2.dp, if (checked) primary else outline, CircleShape)
            .semantics { contentDescription = if (checked) "Подход выполнен. Нажмите, чтобы отменить" else "Отметить подход выполненным" }
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** Шаг веса в единицах отображения. */
fun weightStep(unit: WeightUnit): Double = if (unit == WeightUnit.KG) 2.5 else 5.0

/** Заголовок таблицы подходов; колонки выровнены по [SetRow]. */
@Composable
fun SetTableHeader(loadType: LoadType, unit: WeightUnit, modifier: Modifier = Modifier, showCheck: Boolean = true) {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("№", style = style, color = color, textAlign = TextAlign.Center, modifier = Modifier.size(width = 28.dp, height = 16.dp))
        if (loadType != LoadType.REPS_ONLY) {
            val label = if (loadType == LoadType.BODYWEIGHT) "Доп. вес, ${unit.label}" else "Вес, ${unit.label}"
            Text(label, style = style, color = color, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
        Text("Повторения", style = style, color = color, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        if (showCheck) {
            Text("✓", style = style, color = color, textAlign = TextAlign.Center, modifier = Modifier.size(width = 44.dp, height = 16.dp))
        }
    }
}

/**
 * Строка подхода: номер (по нажатию — меню удаления), вес и повторения с кнопками «−» «+»
 * и крупная отметка выполнения. Значения остаются редактируемыми и после выполнения подхода.
 * Вес хранится в кг, на экране показывается в единицах [unit].
 */
@Composable
fun SetRow(
    number: Int,
    set: SetEntity,
    loadType: LoadType,
    unit: WeightUnit,
    onWeightKg: (Double) -> Unit,
    onReps: (Int) -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    showCheck: Boolean = true,
) {
    val shape = RoundedCornerShape(12.dp)
    val bg = if (set.isCompleted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SetNumberChip(number = number, onDelete = onDelete)

        if (loadType != LoadType.REPS_ONLY) {
            val step = weightStep(unit)
            Stepper(
                onMinus = { onWeightKg(unit.stepKg(set.weight, -step)) },
                onPlus = { onWeightKg(unit.stepKg(set.weight, step)) },
                modifier = Modifier.weight(1f),
                buttonSize = 32.dp,
            ) { m ->
                DecimalInput(
                    valueKg = set.weight,
                    unit = unit,
                    onChangeKg = onWeightKg,
                    modifier = m,
                    placeholder = if (loadType == LoadType.BODYWEIGHT) "+0" else "0",
                )
            }
        }

        Stepper(
            onMinus = { onReps(InputRules.stepReps(set.reps, -1)) },
            onPlus = { onReps(InputRules.stepReps(set.reps, 1)) },
            modifier = Modifier.weight(1f),
            buttonSize = 32.dp,
        ) { m ->
            IntInput(value = set.reps, onChange = onReps, modifier = m)
        }

        if (showCheck) {
            CheckButton(checked = set.isCompleted, onToggle = onToggle, size = 44.dp)
        }
    }
}

@Composable
private fun SetNumberChip(number: Int, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable { menu = true }
                .semantics { contentDescription = "Подход $number. Нажмите для действий" },
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), style = MaterialTheme.typography.labelMedium)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Удалить подход") },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = {
                    menu = false
                    onDelete()
                },
            )
        }
    }
}
