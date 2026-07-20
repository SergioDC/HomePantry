# Nocturne redesign — design spec

Source: Claude Design exploration `Home Pantry - Exploracion.dc.html`
(project `fe601420-3c18-497c-9c6a-d71e8652b86a`), which mocked two full
style directions ("Organic" and "Nocturne") across 5 screens. This spec
covers implementing the **Nocturne** direction as a full navigation
restructure of the existing app.

## Decisions made during brainstorming

- **Style**: Nocturne only (dark navy `#161826`, lavender accent `#9184d9`,
  compact spacing). Organic is not implemented. No system light/dark
  following — always dark, same as today's "always light" behavior, just
  flipped.
- **Scope**: full restructure — bottom nav + new dashboard/detail/search
  screens + restyle of existing screens. Not a color-only reskin.
- **Zone icon/color**: auto-derived (first letter of name + existing
  `zoneColorFor(index)` rotation). No new Zone schema field, no
  icon/color editor UI.
- **Typography**: system default (Roboto), not bundled Inter. Visually
  close enough to the mockup's Inter to not justify sourcing/bundling
  font assets.

## Theme

Replace the current light Mint/Lime `lightColorScheme` in `Theme.kt` with
a dark scheme using Material3's `darkColorScheme`:

| Role | Value |
|---|---|
| background | `#161826` |
| surface / card | `#232532` |
| primary / accent | `#9184d9` |
| onBackground / onSurface (text) | `#e9e9ed` |
| outline (dividers) | `#e9e9ed` @ 16% alpha |

`Color.kt` gets new constants alongside (not replacing) the existing
Mint/Lime ones, since `ZONE_COLORS` (the rotating zone chip palette) is
independent of the theme and stays as-is — it already provides enough
visual variety against the new dark background.

`ListaDeLaCasaTheme` composable keeps its name; only the color scheme
values change.

## Navigation

`MainActivity`'s `NavHost` gains a `Scaffold` wrapper with a custom
bottom bar, shown for the 3 main destinations and hidden for pushed
detail screens:

- `mainList` (Lista tab)
- `zonesDashboard` (Almacén tab)
- `zoneDetail/{zoneId}` (pushed from dashboard; bottom bar stays visible
  with Almacén still highlighted, matching the mockup)
- `search` (Buscar tab)
- `manageZones` (pushed from dashboard's gear icon; no bottom bar, same
  as today)

`JoinHouseholdScreen` and `EditNameDialog` are unaffected — they gate
before the bottom-nav'd app, same as today.

**BottomNavBar** (new composable, `ui/components/BottomNavBar.kt`): 3
items, each a small dot indicator (filled + accent color when active,
outlined when inactive) above a label — matches the mockup's footer,
not Material's default `NavigationBar` icon style.

**FAB**: new shared style — transparent background, `1.5dp` accent
border, accent-colored `+` icon — replacing Material's filled default.
Shown on Lista and Zone Detail; not shown on Dashboard or Search.

## Screen: Zones dashboard (new, `ZonesDashboardScreen.kt`)

Route `zonesDashboard`, the Almacén tab's root.

- Header row: "Alacena" title + gear icon → navigates to `manageZones`.
- Greeting block: "Hola, {userName}" + today's date (same date format
  already used in `MainListScreen`).
- 2-column grid (`LazyVerticalGrid` or manual `Row`/`Column` chunking)
  of zone cards, one per zone (sorted by `order`):
  - Letter icon: zone name's first character, colored background via
    `zoneColorFor(index)`.
  - Zone name.
  - Item summary: `"{itemCount} productos · {pendingCount} pendientes"`,
    or `"{itemCount} productos · al día"` when `pendingCount == 0`.
  - Tapping a card navigates to `zoneDetail/{zone.id}`.
- Dashed "+ Nueva zona" tile at the end of the grid: tapping opens a
  small `AlertDialog` with a text field; confirming calls
  `viewModel.createZone(name)`. (Lighter-weight than navigating to
  `ManageZonesScreen` for this one action.)

## Screen: Zone detail (new, `ZoneDetailScreen.kt`)

Route `zoneDetail/{zoneId}`.

- Header: back chevron → pop, zone name, pencil icon → navigates to
  `manageZones` (rename/delete already live there; no new per-zone
  editor).
- Item list: reuses `groupAndSort` filtered to this one zone (or a
  simpler direct filter — no grouping needed for a single zone), pill
  rows via the new `ItemPillRow` component: round checkbox (tap toggles
  done), name (strikethrough if done), meta line (`"{qty} {unit} ·
  pedido por {addedBy}"`), note in italics if present, "×" tap target
  for delete.
- FAB → opens `AddItemSheet` with `initialZoneId = zoneId`.

## Screen: Lista tab (restyle `MainListScreen.kt`)

- Title changes from app name + date to "Lista de la compra" (the
  app-name + date combo moves to the dashboard greeting instead).
- Progress bar: unchanged (`ProgressBar` component reused as-is).
- **New**: `SegmentedToggle` (new component) for Agrupado / Todo,
  backed by new `ListViewMode` state (see Data changes below). Default:
  `GROUPED` (today's only behavior).
- Zone filter chips replace the current `ScrollableTabRow`: a
  horizontally scrollable row of pill chips ("Todos" + each zone,
  reusing the existing `ZoneChip` component), single-select, wired to
  the existing `selectedZoneId` state — no behavior change, just visual.
- **Grouped mode**: today's behavior — `groupAndSort` output rendered
  as sections with a zone-name kicker header, rows via `ItemPillRow`.
- **Flat mode** (new): a single list via new `flattenAndSort()` (see
  below) — pending items first, then done, each row shows a small
  zone-name tag chip on the trailing edge. Done items additionally show
  an accent-colored "Actualizado en {zone}" note line, matching the
  mockup's screen 3b.
- Inline search bar and its toggle icon are **removed** (superseded by
  the Buscar tab).
- Sort mode (existing `SortMode` dropdown), clear-done, and edit-name
  move into a small overflow (⋮) menu on this screen's top bar — the
  mockup doesn't depict these controls at all, so this is a deliberate
  deviation to avoid regressing existing functionality.
- FAB → `AddItemSheet` with `initialZoneId = null` (or the currently
  selected zone filter, if one is active — same as today's behavior).
- Offline indicator banner: unchanged.

## Screen: Buscar tab (new, `SearchScreen.kt`)

Route `search`.

- Always-visible search `TextField` (not a toggle), reusing
  `viewModel.setSearchQuery` / `filterItemsByQuery`.
- "{n} resultados" count text.
- Result list: `ItemPillRow`-style rows (name, meta) + zone-name tag
  chip + trailing chevron.
- Tapping a result opens `AddItemSheet` in edit mode for that item
  (same interaction as tapping a product's name elsewhere) — the
  mockup doesn't specify a tap target, so this reuses the existing
  pattern.

## Data / logic changes

- `UiState` (`AppViewModel.kt`): add `listViewMode: ListViewMode =
  ListViewMode.GROUPED`.
- `ItemListLogic.kt`: add `enum class ListViewMode { GROUPED, FLAT }`
  and a new pure function:
  ```
  fun flattenAndSort(items: List<Item>, zones: List<Zone>, sortMode: SortMode): List<FlatRow>
  ```
  mirroring `groupAndSort`'s pending-first-then-done ordering but
  ungrouped, with each row carrying its zone name for the tag chip.
- `AppViewModel.kt`: add `setListViewMode(mode: ListViewMode)`.
- No changes to `Zone`, `Item`, repositories, or Firestore schema.

## New / removed components

- New: `BottomNavBar`, `ZoneCard`, `ItemPillRow`, `SegmentedToggle`.
- Removed: `ProductCard` — every list context (Lista, Zone Detail,
  Search) moves to the pill style, so the old card-style row becomes
  dead code once `MainListScreen` is restyled.
- Unchanged: `ProgressBar`, `ZoneChip` (reused for the new filter-chip
  row), `AddItemSheet`, `EditNameDialog`, `JoinHouseholdScreen`,
  `ManageZonesScreen`, `BarcodeScannerView`.

## Out of scope

- Organic style direction (not implemented).
- Per-zone custom icon/color (auto-derived only).
- Bundled Inter font.
- System light/dark theme following.
- A dedicated per-zone rename/delete UI on the Zone Detail screen
  (reuses `ManageZonesScreen`).
