#!/bin/zsh
# Stores the OpenRouter API key as a Supabase secret (never in the repo or the APK).
# Usage: tools/set-openrouter-key.sh   (run from the repo root, after `supabase link`)
set -e
print -n "Pega tu clave de OpenRouter (sk-or-...) y pulsa Enter: "
read -rs KEY
echo
if [[ "$KEY" != sk-or-* ]]; then
  echo "Eso no parece una clave de OpenRouter (debe empezar por sk-or-). No se ha guardado nada."
  exit 1
fi
supabase secrets set "OPENROUTER_API_KEY=$KEY" --yes
unset KEY
echo "Clave guardada en los secretos de Supabase."
