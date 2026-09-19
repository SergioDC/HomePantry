# Mockups: historial de compras con tickets y borrado

Borrador para elegir el diseño. Los datos son de ejemplo. `●` = pestaña activa, `›` = se puede abrir.

Hoy el historial solo agrupa por supermercado y producto (con precio unitario). No se ve
"un ticket", así que no hay dónde borrar un duplicado.

---

## 1. Pantalla principal: tres opciones

### Opción A (recomendada): pestañas «Tickets» y «Productos»

La pestaña **Tickets** es nueva y es donde se borra. La pestaña **Productos** es la pantalla actual, sin cambios.

```
┌──────────────────────────────────────┐
│ ←  Historial de compras              │
│ ┌────────────┬─────────────────────┐ │
│ │ Tickets  ● │ Productos           │ │
│ └────────────┴─────────────────────┘ │
│ ( Todos )( Mercadona )( Lidl )( Aldi)│  ← filtro por súper
│                                      │
│ SEPTIEMBRE 2026                      │
│ ┌──────────────────────────────────┐ │
│ │ Mercadona               12 sep › │ │
│ │ 14 líneas                47,32 € │ │
│ ├──────────────────────────────────┤ │
│ │ Mercadona               12 sep › │ │
│ │ 14 líneas                47,32 € │ │
│ │ [!] Posible duplicado            │ │
│ ├──────────────────────────────────┤ │
│ │ Lidl                     9 sep › │ │
│ │ 8 líneas                 21,10 € │ │
│ └──────────────────────────────────┘ │
│ AGOSTO 2026                          │
│ ┌──────────────────────────────────┐ │
│ │ Sin supermercado        30 ago › │ │
│ │ 5 líneas                 12,80 € │ │
│ └──────────────────────────────────┘ │
│                              ( 📷 )  │  ← escanear ticket
└──────────────────────────────────────┘
```

Pestaña **Productos** (igual que ahora):

```
┌──────────────────────────────────────┐
│ ←  Historial de compras              │
│ ┌────────────┬─────────────────────┐ │
│ │ Tickets    │ Productos         ● │ │
│ └────────────┴─────────────────────┘ │
│ Mercadona                            │
│  Leche entera        1,05 €/ud       │
│  Última compra 12 sep · 3 compras    │
│  Plátano             1,99 €/kg       │
│  Última compra 12 sep · 2 compras    │
│ Lidl                                 │
│  Leche entera        0,89 €/ud       │
│ ...                                  │
└──────────────────────────────────────┘
```

- Ventaja: cada pestaña hace una sola cosa. «Tickets» sirve para revisar y limpiar, «Productos» para comparar precios.
- Coste: dos vistas que mantener.

### Opción B: solo tickets, con buscador

Desaparece la vista por producto. Los precios unitarios se ven al abrir un ticket y en el detalle de un producto (que se abre buscándolo).

```
┌──────────────────────────────────────┐
│ ←  Historial de compras       ( 🔍 ) │
│ ( Todos )( Mercadona )( Lidl )( Aldi)│
│ SEPTIEMBRE 2026                      │
│ ┌──────────────────────────────────┐ │
│ │ Mercadona               12 sep › │ │
│ │ 14 líneas                47,32 € │ │
│ ├──────────────────────────────────┤ │
│ │ Lidl                     9 sep › │ │
│ │ 8 líneas                 21,10 € │ │
│ └──────────────────────────────────┘ │
│                              ( 📷 )  │
└──────────────────────────────────────┘
```

- Ventaja: la pantalla más simple.
- Coste: se pierde la comparación de precios de un vistazo por supermercado.

### Opción C: pantalla actual + botón «Tickets»

```
┌──────────────────────────────────────┐
│ ←  Historial de compras    ( Tickets)│  ← nuevo botón arriba
│ Mercadona                            │
│  Leche entera        1,05 €/ud       │
│  ...                                 │
└──────────────────────────────────────┘
```

Tocar «Tickets» abre la lista de la opción A en una pantalla aparte.

- Ventaja: el cambio más pequeño, no toca lo que ya usas.
- Coste: borrar duplicados queda escondido detrás de un botón.

---

## 2. Detalle de un ticket (igual en A, B y C)

```
┌──────────────────────────────────────┐
│ ←  Mercadona · 12 sep        ( 🗑 )  │  ← borrar ticket entero
│ 14 líneas · 47,32 €                  │
│ ──────────────────────────────────── │
│ Leche entera                   ( 🗑 )│
│ 2 ud · 1,05 €/ud          2,10 €     │
│ Plátano                        ( 🗑 )│
│ 0,85 kg · 3,99 €/kg       3,39 €     │
│ Pan de molde                   ( 🗑 )│
│ 1 ud · 1,45 €/ud          1,45 €     │
│ ...                                  │
└──────────────────────────────────────┘
```

Tocar la papelera de arriba pide confirmación:

```
┌────────────────────────────────┐
│ ¿Eliminar este ticket?         │
│ Mercadona · 12 sep · 47,32 €   │
│ Se borrarán sus 14 líneas.     │
│                                │
│          ( Cancelar )( Eliminar)│
└────────────────────────────────┘
```

---

## 3. Cómo se borra: elige uno

| | Ticket entero | Línea suelta |
|---|---|---|
| **1 (recomendado)** | Papelera arriba + confirmación | Papelera en la fila + aviso «Línea eliminada · Deshacer» |
| **2** | Papelera arriba + confirmación | Deslizar la fila hacia un lado + aviso «Deshacer» |
| **3** | Mantener pulsado un ticket en la lista → selección múltiple → «Eliminar» | Igual que 1 |

- Opción 1: la más visible, no hay gestos que descubrir.
- Opción 2: más limpia, pero el gesto no se ve.
- Opción 3: permite borrar varios duplicados de golpe; es lo más complejo.

---

## 4. Ayuda para detectar duplicados (opcional)

Un ticket se marca **«Posible duplicado»** cuando otro ticket tiene el mismo supermercado, el mismo total y el mismo día.
La marca solo avisa; no borra nada.

```
│ ├──────────────────────────────────┤ │
│ │ Mercadona               12 sep › │ │
│ │ 14 líneas                47,32 € │ │
│ │ [!] Posible duplicado            │ │
│ │ ( Ver el otro )    ( Eliminar )  │ │
│ ├──────────────────────────────────┤ │
```

- Ventaja: encuentras el duplicado sin revisar la lista entera.
- Coste: una regla más que puede equivocarse (dos compras iguales el mismo día en el mismo súper).

---

## 5. Qué pasa con lo que ya tienes guardado

Las compras antiguas no guardan a qué ticket pertenecen. Se agrupan por
supermercado + fecha y hora del escaneo (las líneas de un escaneo comparten la misma fecha),
así que también salen como tickets y se pueden borrar igual.
Las compras nuevas guardarán un id de ticket.
