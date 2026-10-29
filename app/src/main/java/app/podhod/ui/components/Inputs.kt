package app.podhod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.podhod.domain.Fmt
import app.podhod.domain.InputRules
import app.podhod.domain.WeightUnit
import kotlin.math.abs

/** Крупное числовое поле для строки подхода. */
@Composable
private fun NumberBox(
    text: String,
    onText: (String) -> Unit,
    decimal: Boolean,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = text,
        onValueChange = onText,
        modifier = modifier,
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            color = color,
            fontSize = 19.sp,
            textAlign = TextAlign.Center,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (text.isEmpty()) {
                    Text(
                        placeholder,
                        fontSize = 19.sp,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                    )
                }
                inner()
            }
        },
    )
}

/**
 * Поле веса. Значение хранится в кг, на экране показывается в выбранных единицах.
 * Текст поля — локальное состояние, а значение из базы синхронизируется в него только если
 * оно изменилось извне (кнопки −/+, принятая рекомендация): так курсор не «прыгает» при вводе.
 */
@Composable
fun DecimalInput(
    valueKg: Double,
    unit: WeightUnit,
    onChangeKg: (Double) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "0",
) {
    var text by rememberSaveable { mutableStateOf(Fmt.field(unit.fromKg(valueKg))) }
    LaunchedEffect(valueKg, unit) {
        val parsed = InputRules.parseDecimal(text)?.let { unit.toKg(it) } ?: 0.0
        if (abs(parsed - valueKg) > 0.005) text = Fmt.field(unit.fromKg(valueKg))
    }
    NumberBox(
        text = text,
        onText = { raw ->
            val clean = InputRules.sanitizeDecimal(raw)
            text = clean
            onChangeKg(InputRules.parseDecimal(clean)?.let { unit.toKg(it) } ?: 0.0)
        },
        decimal = true,
        placeholder = placeholder,
        modifier = modifier,
    )
}

@Composable
fun IntInput(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "0",
) {
    var text by rememberSaveable { mutableStateOf(Fmt.fieldInt(value)) }
    LaunchedEffect(value) {
        val parsed = InputRules.parseInt(text) ?: 0
        if (parsed != value) text = Fmt.fieldInt(value)
    }
    NumberBox(
        text = text,
        onText = { raw ->
            val clean = InputRules.sanitizeInt(raw)
            text = clean
            onChange(InputRules.parseInt(clean) ?: 0)
        },
        decimal = false,
        placeholder = placeholder,
        modifier = modifier,
    )
}

@Composable
fun StepButton(plus: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier.size(size)) {
        Icon(
            if (plus) Icons.Filled.Add else Icons.Filled.Remove,
            contentDescription = if (plus) "Больше" else "Меньше",
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Поле со значением и кнопками «−» и «+» по бокам. */
@Composable
fun Stepper(
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 36.dp,
    field: @Composable (Modifier) -> Unit,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        StepButton(plus = false, onClick = onMinus, size = buttonSize)
        field(Modifier.weight(1f))
        StepButton(plus = true, onClick = onPlus, size = buttonSize)
    }
}

/** Обычное числовое поле для диалогов; текстом владеет вызывающий код. */
@Composable
fun NumberFormField(
    label: String,
    text: String,
    onText: (String) -> Unit,
    decimal: Boolean,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = text,
        onValueChange = { onText(if (decimal) InputRules.sanitizeDecimal(it) else InputRules.sanitizeInt(it)) },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        suffix = suffix?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
    )
}
