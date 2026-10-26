# Avisos de ofertas

Script que cada lunes cruza las ofertas de Lidl y Aldi con los productos habituales de la casa
(comprados al menos 2 veces en los últimos 90 días según los tickets) y escribe un aviso en
`households/{code}/notifications`. La app lo muestra con la campana de Home. Diseño:
`docs/superpowers/specs/2026-10-26-avisos-ofertas-folletos-design.md`.

Lo ejecuta `.github/workflows/offers.yml` (lunes 07:00 UTC, o a mano desde la pestaña Actions).

## Secrets de GitHub

| Secret | Contenido |
|---|---|
| `FIREBASE_SERVICE_ACCOUNT` | JSON de una cuenta de servicio del proyecto Firebase |
| `HOUSEHOLD_CODE` | Código de la casa |
| `GEMINI_API_KEY` | API key de Gemini |

Opcional: `GEMINI_MODEL` fija un único modelo. Si no, se prueban por orden `gemini-3.8-flash` y
`gemini-3.5-flash`, pasando al siguiente si uno está saturado o no responde. `flash-lite` no se usa:
en las pruebas emparejaba productos que no tenían nada que ver. Además, una oferta solo cuenta si
comparte alguna palabra con el producto habitual (filtro sin IA contra las invenciones).

**El repo es público y sus logs también.** El script solo imprime recuentos. No añadas logs con
productos, ofertas ni el código de casa.

## En local

```bash
cd tools/offers
npm install
npm test
FIREBASE_SERVICE_ACCOUNT_FILE=/ruta/sa.json HOUSEHOLD_CODE=... GEMINI_API_KEY=... \
  node src/index.mjs --dry-run --print > aviso.json
```

`--dry-run` no escribe en Firestore; `--print` vuelca el aviso completo (se ignora en CI).
