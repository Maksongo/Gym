#!/usr/bin/env bash
# Проверка чистого Kotlin-кода (domain + юнит-тесты) БЕЗ Android SDK и интернета:
# используется компилятор Kotlin и JUnit, которые поставляются вместе с Gradle.
# Android/Compose/Room-части этим скриптом не проверяются — их компилирует Gradle.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE_LIB="${GRADLE_LIB:-$(ls -d /opt/gradle*/lib 2>/dev/null | head -1)}"
[ -d "$GRADLE_LIB" ] || { echo "Не найден каталог lib Gradle. Задайте GRADLE_LIB=..." >&2; exit 2; }

OUT="${TMPDIR:-/tmp}/podhody-domain-check"
rm -rf "$OUT"; mkdir -p "$OUT/main" "$OUT/test"

J() { ls "$GRADLE_LIB"/$1 | head -1; }
COMPILER_CP="$(J 'kotlin-compiler-embeddable-*.jar'):$(J 'kotlin-stdlib-*.jar'):$(J 'kotlin-script-runtime-*.jar'):$(J 'kotlin-reflect-*.jar'):$(J 'kotlin-daemon-embeddable-*.jar'):$(J 'trove4j-*.jar'):$(J 'annotations-*.jar'):$(J 'kotlinx-coroutines-core-jvm-*.jar')"
STDLIB="$(J 'kotlin-stdlib-*.jar')"
JUNIT="$(J 'junit-4*.jar'):$(J 'hamcrest-core-*.jar')"

kc() { java -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -nowarn "$@"; }

# Пакеты без зависимостей от Android.
MAIN_SRC=$(find "$ROOT/app/src/main/java/app/podhod/domain" -name '*.kt')
if grep -l "^import android\|^import androidx" $MAIN_SRC >/dev/null 2>&1; then
  echo "В domain найден импорт android/androidx:" >&2
  grep -l "^import android\|^import androidx" $MAIN_SRC >&2
  exit 3
fi

echo "== Компиляция domain"
kc -cp "$STDLIB" -d "$OUT/main" $MAIN_SRC

TESTS=$(find "$ROOT/app/src/test" -name '*.kt' | xargs grep -L "Robolectric\|androidx\.test\|import android" || true)
echo "== Компиляция тестов: $(echo "$TESTS" | wc -l) файлов"
kc -cp "$STDLIB:$JUNIT:$OUT/main" -d "$OUT/test" $TESTS

CLASSES=$(cd "$OUT/test" && find . -name '*Test.class' | sed 's#^\./##; s#\.class$##; s#/#.#g' | sort)
echo "== Запуск JUnit"
java -cp "$STDLIB:$JUNIT:$OUT/main:$OUT/test" org.junit.runner.JUnitCore $CLASSES
