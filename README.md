# Gym en Casa

App Android para entrenar en casa con el material que tengas: gomas, esterilla, mancuerna, foam o lo que haya por casa.

- Las rutinas se generan con IA (OpenRouter) o eligiéndolas tú.
- Cada sesión tiene vídeo, temporizador y pitido.
- Cada amigo tiene su cuenta y su historial en Supabase.
- Se instala por APK y se actualiza sola desde GitHub Releases.

## Instalar en el móvil
1. Abre la página de *Releases* del repositorio en el móvil y descarga el último `gym-en-casa-X.Y.Z.apk`.
2. Ábrelo y permite "instalar apps desconocidas" para el navegador.
3. Las siguientes versiones las ofrece la propia app al arrancar (o en Ajustes → Buscar actualizaciones).

## Desarrollo
Consulta [AGENTS.md](AGENTS.md) (arquitectura, comandos y reglas) y [supabase/README.md](supabase/README.md) (backend).

## Publicar una versión
Primero, configura una vez los **secrets** del repositorio en GitHub (Settings → Secrets and variables → Actions):

| Secret | Valor |
|---|---|
| `KEYSTORE_BASE64` | `base64 -i keystore/release.jks` |
| `KEYSTORE_PASSWORD` / `KEY_PASSWORD` | los de `keystore/keystore.properties` |
| `KEY_ALIAS` | `gymencasa` |
| `SUPABASE_URL` / `SUPABASE_ANON_KEY` | Project Settings → API en Supabase |

Después, cada versión:
```bash
git tag v0.2.0 && git push origin v0.2.0
```
GitHub Actions ejecuta los tests, compila el APK firmado y lo publica como Release.

> Guarda una copia de `keystore/` fuera del ordenador (gestor de contraseñas, por ejemplo). Sin ese keystore no se pueden instalar actualizaciones encima de la app.
