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

**El repo es público y sus logs también.** El script solo imprime recuentos. No añadas logs con
productos, ofertas ni el código de casa.

## Cómo decide qué es una oferta

Solo se avisa del **mismo producto** que compráis: da igual la marca o el tamaño, pero no las
variantes ("Sandía mini" no vale para "sandía"). Para eso hay tres pasos con Gemini (`src/match.mjs`):

1. **Nombres completos.** Los del ticket vienen abreviados ("sandia negra s/n sem") y se pasan a
   nombre completo ("sandía negra sin semillas").
2. **Buscador.** Por cada tienda, Gemini busca candidatos y pone a cada uno una nota de 0 a 100
   con una escala fija y ejemplos (`RUBRIC`).
3. **Revisor.** Otro modelo distinto puntúa las candidatas con la misma escala. En Lidl, además,
   comprueba en el texto de la página que el producto está y cómo se llama.

Solo se avisa si las dos notas llegan a 90 (`MIN_SCORE`) y, además, la oferta comparte alguna
palabra con el producto del ticket (filtro sin IA contra las invenciones). Si el revisor no
responde, esa tienda se da por fallida: mejor sin aviso que con candidatas sin revisar.

Modelos: se prueban por orden `gemini-3.8-flash`, `gemini-3.7-flash`, `gemini-3.6-flash` y
`gemini-3.5-flash`, pasando al siguiente si uno está saturado o no responde. El revisor usa la misma
lista sin el modelo que ha buscado. Los modelos razonan a fondo (`thinkingLevel: high`) y sin
temperatura fija. Los Pro no se usan porque el plan gratuito de Gemini no los incluye, y
`flash-lite` tampoco: en las pruebas emparejaba productos que no tenían nada que ver.

El plan gratuito da 20 llamadas al día por modelo. Una ejecución gasta unas 5.

### Afinar la escala

`eval/cases.json` tiene casos etiquetados a mano (compran → folleto, avisa o no). Para medir cada
modelo con el prompt del revisor:

```bash
GEMINI_API_KEY=... npm run eval                      # todos los modelos de la lista
GEMINI_API_KEY=... npm run eval -- gemini-3.6-flash   # solo uno
```

Si cambias `RUBRIC`, pasa el eval antes y después. Los casos son genéricos: no metas productos de
la casa, porque el repo es público.

## En local

```bash
cd tools/offers
npm install
npm test
FIREBASE_SERVICE_ACCOUNT_FILE=/ruta/sa.json HOUSEHOLD_CODE=... GEMINI_API_KEY=... \
  node src/index.mjs --dry-run --print > aviso.json
```

`--dry-run` no escribe en Firestore; `--print` vuelca el aviso completo y las notas y razones del
buscador y del revisor de cada tienda (se ignora en CI).
