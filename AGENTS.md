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
    EquipmentFilter  ejercicio disponible solo si el usuario tiene TODO su material
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
- Paleta **rojo / negro / gris**. Tema oscuro por defecto y claro opcional.
- Colores definidos **solo** en `ui/theme/Theme.kt` (`GymColors` + `ColorScheme`). No uses `Color(0x…)` fuera de `ui/theme`; usa `MaterialTheme.colorScheme`.
- Titulares en mayúsculas con la fuente condensada del sistema (`Condensed`). Números del temporizador con `TimerStyle`.
- Sesión: anillo rojo durante el trabajo, gris durante el descanso y pulso rojo en los últimos 3 s.
- Vídeos: figura gris claro con camiseta roja sobre fondo `#1A1A1A`, 480×480.

## Reglas obligatorias
- **Secretos**: nunca metas la clave de OpenRouter ni la `service_role` de Supabase en el APK ni en el repo.
  - La de OpenRouter solo va en `supabase secrets`.
  - La anon key sí puede ir en el APK: la protege RLS.
- **RLS**: toda tabla nueva lleva RLS y políticas `user_id = (select auth.uid())`, o de solo lectura si es catálogo.
- **Descanso**: el descanso entre series sale siempre de `UserSettings.restBetweenSetsSeconds`. No lo guardes por rutina ni lo decida la IA.
- **Material**: todo ejercicio declara su `equipment[]` (lista completa) y solo se muestra o sugiere si el usuario lo tiene todo (`EquipmentFilter`). `ninguno` siempre está disponible.
- **IA**: la IA solo elige y ordena slugs. Valida siempre la respuesta contra el catálogo disponible (en la función y en `AiRoutineRepository`). Si falla, usa `SuggestionEngine`.
- **Textos**: todos los textos de UI en español, en `res/values/strings.xml`.
- **Dominio**: la lógica de dominio va en Kotlin puro dentro de `domain/` y con tests en `app/src/test`.
- **Firma**: no cambies el keystore (`keystore/release.jks`). Si cambia, las actualizaciones no se instalan encima.
- **Versionado**: cada release sube la versión con el tag `vX.Y.Z` (`versionCode = X*10000 + Y*100 + Z`).
- **Esquema**: no edites a mano `supabase/seed.sql` ni `assets/catalog/exercises.json`; se generan desde `tools/videogen/exercises.py`.

## Esquema de datos (resumen)
- `equipment(slug, name)` y `exercises(slug, name, description, muscle_groups[], equipment[], type, difficulty, unilateral, video_path, thumb_path)`: catálogo de solo lectura.
- `profiles(id = auth.uid, display_name, level, goal, equipment[], rest_between_sets_s)`. Se crea con un trigger al registrarse.
- `workout_sessions(id, user_id, title, mode ai|manual, goal, duration_target_min, started_at, finished_at, completed)` y `session_sets(session_id, position, exercise_slug, set_index, planned_s, skipped)`.
- `routines(id, user_id, name, goal, duration_target_min)` y `routine_items(routine_id, position, exercise_slug, sets, work_s)`.

Las migraciones son nuevas siempre (`supabase/migrations/AAAAMMDDHHMMSS_nombre.sql`); nunca edites una ya aplicada.

## Pipeline de vídeos
Esqueleto 2D con keyframes, en `tools/videogen/skeleton.py`:
- Por cada extremidad, ángulos (`fk`) u objetivos de IK (`ik`, `ikj`).
- Interpolación suave y bucle sin saltos.
- Render con Pillow a 2× y reducción a la mitad.
- Codificación H.264 (CRF 30, 24 fps, sin audio) de unos 15–25 KB por vídeo.

Para añadir un ejercicio:
1. Añade un `ex(...)` en `tools/videogen/exercises.py`: metadatos (slug, nombre, descripción, `muscle_groups`, `equipment`, `type`, dificultad, unilateral), keyframes y props (`chair`, `wall`, `step`, `sofa`, `mat`, `foam`, `post`, `towel`, `backpack`, `dumbbell`, `bottle`, `band`).
2. Revísalo con `sheet.py`.
3. `render.py --to-app` y `upload.py` (más `--upload` si quieres el vídeo en Storage).
4. `supabase db push --include-seed`.

Grupos musculares válidos (los usa el filtro por zona): `pecho, espalda, hombros, brazos, biceps, triceps, piernas, cuadriceps, isquios, gluteos, gemelos, aductores, core, abdominales, oblicuos, lumbar, cardio, cuerpo_completo`.

## Skills recomendadas
Hay instaladas android-skills (plugin `android-skills@android-skills`):
- `navigation-3`: rutas y back stack.
- `edge-to-edge`: insets.
- `testing-setup`: tests de UI y screenshots.
- `jetpack-compose-performance`.
- `android-cli`: emulador, instalar y capturas.
- `agp-9-upgrade`: el proyecto usa AGP 9 con Kotlin integrado; no apliques `org.jetbrains.kotlin.android`.
