# Backend Supabase (plan gratuito)

Contenido:
- `migrations/` — esquema con RLS en todas las tablas.
  - Catálogo de lectura: `equipment`, `exercises`.
  - Datos de cada usuario: `profiles` (con el descanso global entre series), `workout_sessions`, `session_sets`, `routines` y `routine_items`.
  - Bucket público `exercise-media`.
- `seed.sql` — catálogo de ejercicios (generado por `tools/videogen/upload.py`; no editar a mano).
- `functions/generate-routine` — Edge Function que llama a OpenRouter. La clave **solo** vive en los secretos de Supabase.

## Puesta en marcha (una vez)

1. Crea un proyecto gratuito en https://supabase.com. Apunta el *Project ref* y la *anon key* (Project Settings → API).
2. Authentication → Sign In / Providers → Email: **desactiva "Confirm email"**, para que tus amigos entren al momento.
3. Enlaza y aplica el esquema y el catálogo:
   ```bash
   supabase login
   supabase link --project-ref TU_PROJECT_REF
   supabase db push --include-seed
   ```
4. Clave de OpenRouter (créala en https://openrouter.ai/keys). Ejecútalo tú, no la pegues en ningún archivo:
   ```bash
   supabase secrets set OPENROUTER_API_KEY=sk-or-...
   # opcional: otro modelo (los ":free" son gratuitos, pero cambian con el tiempo)
   supabase secrets set OPENROUTER_MODEL=meta-llama/llama-3.3-70b-instruct:free
   ```
5. Despliega la función:
   ```bash
   supabase functions deploy generate-routine
   ```
6. (Opcional) Sube los vídeos a Storage. La app ya los lleva dentro del APK; Storage sirve para ejercicios nuevos sin publicar otra versión:
   ```bash
   SUPABASE_URL=https://TU_PROJECT_REF.supabase.co SUPABASE_SERVICE_ROLE_KEY=... \
     tools/videogen/.venv/bin/python -I tools/videogen/upload.py --upload
   ```
7. Pon la URL y la anon key en `local.properties` (compilación local) y en los secrets de GitHub (CI): `SUPABASE_URL` y `SUPABASE_ANON_KEY`. La anon key está pensada para ir en la app: la protege RLS.

## Probar la función con curl

```bash
# 1) obtener un token de usuario (email/contraseña de una cuenta de prueba)
TOKEN=$(curl -s "https://TU_PROJECT_REF.supabase.co/auth/v1/token?grant_type=password" \
  -H "apikey: $ANON_KEY" -H "Content-Type: application/json" \
  -d '{"email":"prueba@example.com","password":"..."}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')

# 2) pedir una rutina
curl -s "https://TU_PROJECT_REF.supabase.co/functions/v1/generate-routine" \
  -H "Authorization: Bearer $TOKEN" -H "apikey: $ANON_KEY" -H "Content-Type: application/json" \
  -d '{"goal":"fuerza","duration_min":20,"focus":"cuerpo_completo","level":"intermedio"}'
# -> {"title":"...","exercise_slugs":["sentadilla",...],"source":"ai"}

# 3) sin token debe responder 401
curl -s -o /dev/null -w '%{http_code}\n' -X POST "https://TU_PROJECT_REF.supabase.co/functions/v1/generate-routine"
```

## Límites del plan gratuito

500 MB de base de datos, 1 GB de Storage, 5 GB de transferencia al mes y 50k usuarios activos al mes. **El proyecto se pausa tras 7 días sin actividad.** El workflow `.github/workflows/keepalive.yml` hace una consulta cada 3 días para evitarlo.
