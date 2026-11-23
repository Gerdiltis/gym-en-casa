# AGENTS.md — Gym en Casa

Guía para agentes de IA (Claude Code, Codex, Gemini…) que trabajen en este repositorio. Mantenla al día cuando cambie la arquitectura, los comandos o las reglas.

## Producto
App Android privada (se instala por APK, sin Play Store) para entrenar en casa con el material que cada usuario tiene. Flujo principal:

**Mi material → ¿cuánto tiempo tienes hoy? → IA o manual → Plan del día → Sesión → Resumen**

- **IA**: la Edge Function `generate-routine` (OpenRouter) elige y ordena los ejercicios.
- **Manual**: `SuggestionEngine` propone ejercicios que caben en la duración y el usuario los ajusta.
- **Series y segundos** los asigna siempre `PlanAlgorithm` (determinista). El **descanso entre series es global**: sale del perfil del usuario.
- **Sesión**:
  1. Preview de 10 s con el vídeo en bucle.
  2. Series con temporizador y pitido al terminar cada una.
  3. Descanso.
  4. Al acabar el ejercicio, el usuario pulsa "Empezar siguiente ejercicio".

## Arquitectura
Kotlin + Jetpack Compose (Material 3) · MVVM/UDF · Hilt · Navigation 3 · Room (caché) · supabase-kt (Auth, Postgrest, Functions) · Media3 ExoPlayer · WorkManager · DataStore. Un solo módulo `app`.

```
app/src/main/java/com/gymencasa/
  domain/            Kotlin puro y testeado (sin Android)
    model/           Exercise, Equipment, Goal, Level, Focus, DayPlan, UserSettings
    ExerciseFilter   disponible si tiene TODO su material ("a|b" = alternativas) y, en modo embarazo, si es apto
    PlanAlgorithm    series × segundos por objetivo/nivel + ajuste a la duración
    SuggestionEngine propuesta equilibrada que cabe en la duración (y fallback de la IA)
    session/SessionStateMachine  PREVIEW → WORK → REST → … → AWAIT_NEXT → FINISHED
  data/
    local/           Room: exercises, workouts, workout_sets, routines, routine_items
    remote/          SupabaseProvider (cliente nulo si la build no tiene URL/key) + DTOs
    repository/      Auth, Settings (DataStore + profiles), Exercise, Workout, AiRoutine
    sync/SyncWorker  sube sesiones y rutinas pendientes (offline-first)
    DayDraftStore    estado compartido del día (duración, objetivo, selección, plan)
  ui/                pantallas Compose + ViewModels (una carpeta por pantalla), theme/
  update/GitHubUpdater  auto-actualización desde GitHub Releases
supabase/            migraciones SQL (RLS), seed.sql generado, Edge Function generate-routine
tools/videogen/      generador de vídeos de ejercicios (Python + Pillow + ffmpeg)
.github/workflows/   ci.yml, release.yml (tag → APK firmado), keepalive.yml (Supabase)
```

Fuentes de verdad:
- **Supabase** es la fuente de verdad de los datos de usuario. Room es caché, y las escrituras se hacen primero en local y luego se sincronizan.
- El **catálogo** va empaquetado en `app/src/main/assets/catalog/exercises.json` y se refresca desde la tabla `exercises`.
- Los **vídeos** van empaquetados en `assets/videos` y `assets/thumbs`. Si falta alguno, se usa Storage con caché de ExoPlayer.

## Comandos
Entorno: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=$HOME/Library/Android/sdk`

| Tarea | Comando |
|---|---|
| Tests de dominio | `./gradlew testDebugUnitTest` |
| APK debug | `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` |
| APK release firmado (local) | `./gradlew assembleRelease` (usa `KEYSTORE_*` de `local.properties`) |
| Instalar en emulador/móvil | `adb install -r app/build/outputs/apk/debug/app-debug.apk` |
| Regenerar vídeos + catálogo en el APK | `tools/videogen/.venv/bin/python -I tools/videogen/render.py --to-app` |
| Hoja de revisión visual | `tools/videogen/.venv/bin/python -I tools/videogen/sheet.py out/sheet.png slug1 slug2` |
| Regenerar `supabase/seed.sql` | `tools/videogen/.venv/bin/python -I tools/videogen/upload.py` |
| Aplicar esquema + seed | `supabase db push --include-seed` |
| Desplegar la función IA | `supabase functions deploy generate-routine` |
| Publicar versión | `git tag v0.2.0 && git push origin v0.2.0` (Actions compila, firma y crea el Release) |

Configuración: `local.properties` (no versionado) o variables de entorno en CI: `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `GITHUB_REPO` (`owner/repo`), `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `VERSION_NAME`, `VERSION_CODE`.

## Guía visual
Estilo moderno, claro y redondeado, inspirado en apps de asistente. Paleta **rojo / negro / gris**. **Tema claro por defecto** y oscuro opcional en Ajustes.

- **Tokens**: colores solo en `ui/theme/Theme.kt` (`GymColors`, `ColorScheme` y `LocalGymExtras`, que trae el degradado rojo, las sombras y los colores de los puntos musculares). No uses `Color(0x…)` fuera de `ui/theme`.
- **Componentes** (`ui/components/Components.kt`), en lugar de los de Material directamente:
  - `GymCard`: tarjeta blanca de 22 dp con sombra suave.
  - `HeroCard`: tarjeta con degradado rojo.
  - `PillChip`: píldora seleccionable; con `onHero`, para usar dentro de la tarjeta roja.
  - `GradientButton`: CTA principal en píldora roja.
  - `PillButton`: acción secundaria.
  - `RoundIconButton`.
  - `FloatingPill` y `TopPillBar`: barra superior flotante con atrás, título y acción.
  - `CardListItem`: filas de lista como tarjetas.
  - `SectionTitle`: etiqueta en mayúsculas espaciadas tipo "PARA TI".
  - `ScreenHeader`: titular grande en *sentence case*.
- **Navegación**: barra de pestañas flotante en píldora; la pestaña activa es una píldora roja con su texto.
- **Tipografía**: sans del sistema en pesos ExtraBold para los titulares. Botones y títulos en *sentence case* (no en mayúsculas); las mayúsculas solo para etiquetas de sección.
- **Plan del día**: estilo chat. La petición va en una burbuja roja y la respuesta es una tarjeta con la lista: punto de color por músculo principal y píldora "3 × 45 s". "Iniciar sesión" y "Otra propuesta" van en la barra flotante inferior.
- **Sesión**: estilo "voice mode", con la píldora "EJERCICIO X DE N", el vídeo redondeado y el anillo con degradado rojo (gris durante el descanso y pulso rojo en los últimos 3 s). Los controles van en una píldora flotante: cerrar, pausa grande y saltar.
- **Vídeos**: avatar ilustrado (cabeza real, ropa azul marino) sobre fondo gris claro `#F3F4F7`, 720×720, con los músculos en rojo.

## Reglas obligatorias
- **Secretos**: nunca metas la clave de OpenRouter ni la `service_role` de Supabase en el APK ni en el repo.
  - La de OpenRouter solo va en `supabase secrets`.
  - La anon key sí puede ir en el APK: la protege RLS.
- **RLS**: toda tabla nueva lleva RLS y políticas `user_id = (select auth.uid())`, o de solo lectura si es catálogo.
- **Descanso**: el descanso entre series sale siempre de `UserSettings.restBetweenSetsSeconds`. No lo guardes por rutina ni lo decida la IA.
- **Material**: todo ejercicio declara su `equipment[]` (lista completa) y solo se muestra o sugiere si el usuario lo tiene todo (`ExerciseFilter`).
  - `ninguno` siempre está disponible.
  - Un elemento puede tener alternativas separadas por `|` (por ejemplo `gomas|bandas_largas`).
- **Embarazo**: cada ejercicio tiene `pregnancy` = `apto`, `parto` (preparación al parto) o `null` (no recomendado).
  - Con `UserSettings.pregnant`, la app, el `SuggestionEngine` y la Edge Function solo usan apto o parto, priorizan parto y limitan la dosis (`PlanAlgorithm`: 3 series y 45 s como máximo).
  - Criterio conservador: sin saltos ni impactos, nada tumbado boca arriba o boca abajo, sin core intenso ni cargas lumbares.
  - Muestra siempre el aviso de consultar con la matrona y no prometas efectos sobre el parto.
- **Músculos**: `muscle_groups` va ordenado, con el principal primero; la UI lo muestra en rojo y el resto en gris.
- **IA**: la IA solo elige y ordena slugs. Valida siempre la respuesta contra el catálogo disponible (en la función y en `AiRoutineRepository`). Si falla, usa `SuggestionEngine`.
- **Textos**: todos los textos de UI en español, en `res/values/strings.xml`.
- **Dominio**: la lógica de dominio va en Kotlin puro dentro de `domain/` y con tests en `app/src/test`.
- **Firma**: no cambies el keystore (`keystore/release.jks`). Si cambia, las actualizaciones no se instalan encima.
- **Versionado**: cada release sube la versión con el tag `vX.Y.Z` (`versionCode = X*10000 + Y*100 + Z`).
- **Esquema**: no edites a mano `supabase/seed.sql` ni `assets/catalog/exercises.json`; se generan desde `tools/videogen/exercises.py`.

## Esquema de datos (resumen)
- `equipment(slug, name)` y `exercises(slug, name, description, muscle_groups[], equipment[], type, difficulty, unilateral, video_path, thumb_path, pregnancy)`: catálogo de solo lectura.
- `profiles(id = auth.uid, display_name, level, goal (incluye `parto`), equipment[], rest_between_sets_s, pregnant)`. Se crea con un trigger al registrarse.
- `workout_sessions(id, user_id, title, mode ai|manual, goal, duration_target_min, started_at, finished_at, completed)` y `session_sets(session_id, position, exercise_slug, set_index, planned_s, skipped)`.
- `routines(id, user_id, name, goal, duration_target_min)` y `routine_items(routine_id, position, exercise_slug, sets, work_s)`.

Las migraciones son nuevas siempre (`supabase/migrations/AAAAMMDDHHMMSS_nombre.sql`); nunca edites una ya aplicada.

## Pipeline de vídeos
Esqueleto 2D con keyframes, en `tools/videogen/skeleton.py`:
- Por cada extremidad, ángulos (`fk`) u objetivos de IK (`ik`, `ikj`).
- Interpolación suave y bucle sin saltos.
- Render a 2× y reducción a la mitad.

Hay dos estilos en `render.py`:
- **`illustrated`** (por defecto, desde la v0.4.0). Lo dibuja `illustrated.py`: avatar con la **cabeza real** recortada de la hoja de personaje del usuario (`assets/avatar_sheet.webp`, con `extract_avatar.py` → `assets/avatar_head_{side,front}.png`), cuerpo con formas anatómicas, contorno tipo cómic y la ropa de la referencia. Los **músculos trabajados se marcan en rojo sobre el cuerpo** (`muscles.py`):
  - Principal intenso, con pulso.
  - Secundarios más suaves.
  - En `cardio` o `cuerpo_completo` se pone el contorno rojo.
  - Formato: 720×720, 24 fps, CRF 26, miniaturas WebP de 360 px; unos 80 KB por vídeo.
- **`--flat`**: el monigote original, en 480 px y CRF 30.

Para añadir un ejercicio:
1. Añade un `ex(...)` en `tools/videogen/exercises.py`: metadatos (slug, nombre, descripción, `muscle_groups` con el principal primero, `equipment`, `type`, dificultad, unilateral, `pregnancy`), keyframes y props (`chair`, `wall`, `step`, `sofa`, `mat`, `foam`, `ball`, `post`, `towel`, `backpack`, `dumbbell`, `bottle`, `band`).
2. Revísalo con `sheet.py`.
3. `render.py --to-app` y `upload.py` (más `--upload` si quieres el vídeo en Storage).
4. `supabase db push --include-seed`.

Si cambia la hoja del avatar, vuelve a ejecutar `extract_avatar.py`; las coordenadas del recorte están en ese script.

Material válido: `ninguno, gomas, bandas_largas, esterilla, mancuerna, foam, pelota, silla, pared, escalon, sofa, toalla, mochila, botellas`.

Grupos musculares válidos (los usa el filtro por zona): `pecho, espalda, hombros, brazos, biceps, triceps, piernas, cuadriceps, isquios, gluteos, gemelos, aductores, core, abdominales, oblicuos, lumbar, cardio, cuerpo_completo`.

## Skills recomendadas
Hay instaladas android-skills (plugin `android-skills@android-skills`):
- `navigation-3`: rutas y back stack.
- `edge-to-edge`: insets.
- `testing-setup`: tests de UI y screenshots.
- `jetpack-compose-performance`.
- `android-cli`: emulador, instalar y capturas.
- `agp-9-upgrade`: el proyecto usa AGP 9 con Kotlin integrado; no apliques `org.jetbrains.kotlin.android`.
