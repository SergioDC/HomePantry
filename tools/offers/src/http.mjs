// Cabeceras de navegador: sin ellas las webs de las tiendas devuelven errores o páginas vacías.
export const BROWSER_HEADERS = {
  "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130 Safari/537.36",
  "Accept-Language": "es-ES,es;q=0.9",
};

/**
 * GET que devuelve el texto o lanza un error con estado y host. Nunca incluye la ruta ni la query
 * en el mensaje: los logs de Actions son públicos.
 */
export async function getText(url) {
  const response = await fetch(url, { headers: BROWSER_HEADERS });
  if (!response.ok) throw new Error(`${response.status} ${new URL(url).host}`);
  return response.text();
}
