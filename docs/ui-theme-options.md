# UI Theme Direction Options

Alternatives to standard Material 3 for a premium media player feel.
Evaluated for both mobile (touch) and TV (D-pad) platforms.

---

## Option 1: Content-First / Netflix Style
Strip away visible containers. Let poster art and thumbnails BE the interface.
- **Cards:** No borders, no elevation — just images with gradient text overlays fading from bottom
- **Navigation:** Horizontal carousels instead of grid, hero banner at top with backdrop
- **Focus (TV):** Subtle scale-up + drop shadow instead of colored border
- **Feel:** Immersive, cinematic, premium
- **Downside:** Requires poster/thumbnail art from the provider. Xtream/IPTV often has none — you'd need fallback designs for text-only items

## Option 2: Glassmorphism (Apple TV / visionOS)
Frosted translucent panels with blur effects over background imagery.
- **Surfaces:** Frosted translucent panels with `BlurEffect` or `RenderEffect.createBlurEffect` over a background image
- **Cards:** Semi-transparent with subtle 1px light border, backdrop blur
- **Overlays:** Player controls, dialogs all use frosted glass
- **Feel:** Modern, premium, depth-layered
- **Downside:** `RenderEffect` blur is API 31+ and can be expensive on low-end TV chipsets (Sony Bravia). Would need a solid fallback for older devices

## Option 3: Minimal Dark / Spotify-esque
Ultra-clean, typography-driven. No cards at all — just text lists with smart spacing.
- **Layout:** Dense lists with bold titles, muted metadata, tight line-height
- **Color:** Near-black background, single accent color, no surface/card distinction
- **Focus (TV):** Background highlight bar (like a list selector) instead of card glow
- **Navigation:** Collapsible side rail on TV, bottom bar on mobile (current)
- **Feel:** Fast, information-dense, utilitarian-premium
- **Downside:** Less visually exciting for a "media" app. Works better for music/audio than video

## Option 4: Editorial / Magazine Layout
Asymmetric grid with mixed card sizes — hero items get large tiles, others get compact rows.
- **Grid:** Staggered layout — featured content gets 2x width, recent gets compact horizontal scroll
- **Typography:** Large serif or display font for titles, editorial feel
- **Imagery:** Full-bleed posters with text overlay, no visible card boundaries
- **Feel:** Curated, premium, Apple TV+ editorial vibe
- **Downside:** Complex layout logic, harder to make D-pad friendly with irregular grid

## Option 5: Neumorphism-lite / Soft UI
Soft shadows, inset/outset illusion on a mid-dark surface.
- **Cards:** Soft outer shadow + inner highlight edge, no hard borders
- **Buttons:** Pressed = inset shadow, default = raised
- **Feel:** Tactile, modern, distinctive
- **Downside:** Already falling out of trend. Accessibility concerns (low contrast). Looks bad on AMOLED pure black

---

## Chosen Direction (implemented)

**Option 1 + Option 2 hybrid: Content-First with Glassmorphism accents**

- Category grid: Poster thumbnails with gradient text overlay (no card borders). Scale-up on focus.
- Player controls / overlays / dialogs: Frosted glass panels
- Content type selection: Hero backdrop with blur, large type
- Fallback for no-art items: Solid dark card with accent-colored icon + category initial letter
- Keeps existing theme system (accent colors per theme), changes how surfaces and cards feel
- D-pad focus: scale + subtle glow, no boxy card borders
