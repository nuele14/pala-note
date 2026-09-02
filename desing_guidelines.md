ign System per App Android: "PixelSurf"

> Un documento di design che fonde l'estetica pixel/tech di **grilledpixels.com** con l'energia e la chiarezza di **surfing.academy**, declinato in due temi (scuro e chiaro) per un'app nativa Android.

---

## 1. Visione del Progetto

L'app deve comunicare **innovazione, creatività e affidabilità**, unendo:

- Un forte carattere **tecnologico e "pixeloso"** (tipografia monospace, angoli vivi, micro-interazioni "da terminale").
- Una struttura **chiara, modulare e centrata sul contenuto** (ispirata ai block‑based layout di Tilda).
- Due modalità visive:
  - **Tema Scuro** – bianco e nero, minimalista, potente, cyber.
  - **Tema Chiaro** – bianco e arancione, energico, caldo, leggibile.

L'esperienza utente deve essere fluida, reattiva e memorabile, con attenzione ai dettagli che raccontano la personalità del brand.

---

## 2. Palette Colori

### Tema Scuro (Dark)

| Ruolo                   | Colore (HEX)  | Descrizione                      |
| ----------------------- | ------------- | -------------------------------- |
| `background` / `surface`| `#000000`     | Nero puro, fondo principale      |
| `surfaceContainer`      | `#121212`     | Grigio scurissimo per card/aree  |
| `onBackground` / `text` | `#FFFFFF`     | Bianco per testi e icone         |
| `onSurfaceVariant`      | `#B0B0B0`     | Grigio chiaro per sottotitoli    |
| `primary` / `accent`    | `#FFFFFF`     | Bianco (usato per pulsanti e link)|
| `secondary`             | `#888888`     | Grigio medio per elementi secondari|
| `divider`               | `#333333`     | Linee di separazione             |

> **Nota:** nel tema scuro l’accento è il bianco stesso; nessun colore acceso per mantenere l’estetica minimalista e “tech”.

---

### Tema Chiaro (Light)

| Ruolo                   | Colore (HEX)  | Descrizione                      |
| ----------------------- | ------------- | -------------------------------- |
| `background` / `surface`| `#FFFFFF`     | Bianco, fondo principale         |
| `surfaceContainer`      | `#F8F8F8`     | Grigio leggerissimo per card     |
| `onBackground` / `text` | `#000000`     | Nero per testi e icone           |
| `onSurfaceVariant`      | `#555555`     | Grigio scuro per sottotitoli     |
| `primary` / `accent`    | `#FF8562`     | Arancione/corallo per pulsanti e link|
| `secondary`             | `#E0E0E0`     | Grigio chiaro per elementi secondari|
| `divider`               | `#E0E0E0`     | Linee di separazione             |

> L’arancione (`#FF8562`) è lo stesso di surfing.academy e dona calore ed energia al tema chiaro.

---

## 3. Tipografia

Utilizzeremo un **mix di due famiglie** per creare una gerarchia distintiva.

| Stile                   | Font                    | Peso   | Dimensione (sp) | Utilizzo                            |
| ----------------------- | ----------------------- | ------ | --------------- | ----------------------------------- |
| **Display / Hero**      | `Press Start 2P` (o `JetBrains Mono`) | Bold   | 34‑48           | Titoli principali, schermate di benvenuto |
| **Headline (H1‑H3)**    | `JetBrains Mono`        | Bold   | 24‑34           | Sezioni, titoli delle card          |
| **Subtitle (H4‑H5)**    | `JetBrains Mono`        | Medium | 18‑22           | Sottotitoli, etichette importanti   |
| **Body (testo lungo)**  | `Inter` (o `Roboto`)    | Regular| 14‑16           | Descrizioni, paragrafi, messaggi    |
| **Label / Button**      | `JetBrains Mono`        | Bold   | 14‑16           | Pulsanti, link, azioni              |
| **Caption / Meta**      | `JetBrains Mono`        | Regular| 10‑12           | Dati tecnici, date, piccoli dettagli|

> **Nota:** `JetBrains Mono` è un font monospace moderno, molto leggibile, con un tocco “coding” che richiama l’estetica pixel senza essere troppo retrò. Per un effetto più pixelato si può optare per `Press Start 2P`, ma va usato con moderazione (solo per titoli importanti) data la sua minore leggibilità a corpo piccolo.

---

## 4. Layout e Componenti UI

L’app adotta una **struttura modulare a card** con **angoli vivi (nessun arrotondamento)** per enfatizzare il carattere “pixel”.

### 4.1 Schermata Principale (Home)

- **Hero**: immagine a tutta larghezza (o un pattern pixel art) con titolo in `Press Start 2P` e un pulsante CTA in stile “terminale” (bordato, sfondo trasparente).
- **Sezioni**:
  - **Servizi / Funzionalità**: griglia di card (2 colonne) con titolo in monospace e descrizione in Inter.
  - **Testimonianze**: card orizzontali con foto utente, nome (monospace) e testo (Inter).
  - **CTA finale**: grande pulsante arancione (tema chiaro) o bianco (tema scuro) con testo in monospace.

### 4.2 Componenti Chiave

- **Card**:
  - Sfondo: `surfaceContainer` (o trasparente con bordo sottile).
  - Bordo: `1dp` solido (colore `divider`).
  - Ombra: nessuna (preferiamo la planarità).
  - Padding interno: `16dp`.

- **Pulsanti**:
  - **Primario**: sfondo pieno (arancione o bianco), testo monospace bold, angoli vivi.
  - **Secondario**: solo bordo, sfondo trasparente.
  - **Tertiary**: testo semplice senza bordo.

- **Navigazione**:
  - Bottom navigation bar con icone e label in monospace (solo testo, niente icone? Oppure icone minimali in stile “pixel”).
  - Alternativa: drawer laterale con voci in monospace, stile “riga di comando”.

- **Input e Form**:
  - Campi con bordo sottile, senza arrotondamenti, font monospace per il testo inserito.
  - Placeholder in colore secondario.

---

## 5. Micro‑interazioni e Animazioni

Per dare vita all’app e richiamare lo spirito di `grilledpixels.com`, integriamo animazioni brevi e “snappy”.

- **Hover / Touch feedback**: cambio di colore del bordo o del testo con transizione `150ms` ease-in-out.
- **Effetto “Glitch”**: quando si preme un pulsante o si passa sopra un titolo, il testo subisce un leggero sdoppiamento orizzontale per `200ms` (usando `TextView` con `translationX` e `alpha`).
- **Scrolling**: parallax leggero sulle immagini di testa.
- **Transizioni tra schermi**: fade incrociato con curva `FastOutLinearIn` (tipica di Material) ma con durata ridotta (`250ms`) per un feeling più “tecnologico”.
- **Mouse/Touch Trailer**: un piccolo reticolo o quadrato (4x4dp) che segue il dito sullo schermo quando si interagisce con elementi interattivi (opzionale, da valutare per dispositivi con touch).

---

## 6. Implementazione Temi (Dark / Light) in Android

Utilizzeremo **Material Design 3** con personalizzazioni tramite `Theme.Material3.DayNight` e override dei colori e dei font.

### 6.1 Configurazione dei Font

Aggiungere i font nel progetto (es. `res/font/jetbrains_mono_*.ttf` e `inter_*.ttf`) e definire i `TextAppearance` in `themes.xml`.

Esempio di `TextAppearance` per i titoli:

```xml
<style name="TextAppearance.PixelSurf.Headline" parent="TextAppearance.Material3.HeadlineMedium">
    <item name="fontFamily">@font/jetbrains_mono_bold</item>
    <item name="android:textSize">28sp</item>
    <item name="android:textColor">?attr/colorOnBackground</item>
</style>
```

### 6.2 Tema Scuro (valori in `values-night/themes.xml`)

```xml
<style name="Theme.PixelSurf.Dark" parent="Theme.Material3.DayNight.NoActionBar">
    <!-- Colori -->
    <item name="colorPrimary">@android:color/white</item>
    <item name="colorOnPrimary">@android:color/black</item>
    <item name="colorSurface">@android:color/black</item>
    <item name="colorOnSurface">@android:color/white</item>
    <item name="colorSurfaceVariant">#121212</item>
    <item name="colorOnSurfaceVariant">#B0B0B0</item>
    <!-- ... altri override -->
</style>
```

### 6.3 Tema Chiaro (valori in `values/themes.xml`)

```xml
<style name="Theme.PixelSurf.Light" parent="Theme.Material3.DayNight.NoActionBar">
    <!-- Colori -->
    <item name="colorPrimary">#FF8562</item>
    <item name="colorOnPrimary">@android:color/white</item>
    <item name="colorSurface">@android:color/white</item>
    <item name="colorOnSurface">@android:color/black</item>
    <item name="colorSurfaceVariant">#F8F8F8</item>
    <item name="colorOnSurfaceVariant">#555555</item>
    <!-- ... -->
</style>
```

### 6.4 Attivazione del Tema

L’utente potrà cambiare tema tramite un interruttore nelle impostazioni. In `Activity` si usa:

```kotlin
AppCompatDelegate.setDefaultNightMode(
    if (isDark) AppCompatDelegate.MODE_NIGHT_YES
    else AppCompatDelegate.MODE_NIGHT_NO
)
```

---

## 7. Linee Guida per le Immagini e le Icone

- **Immagini**: privilegiare foto ad alto contrasto, possibilmente in bianco e nero (per il tema scuro) o con dominanti calde (per il tema chiaro). Si può applicare un filtro “pixelato” (riduzione della risoluzione) su alcune immagini di sfondo per rafforzare il tema.
- **Icone**: usare icone vettoriali semplici, con tratti netti e spigolosi (stile “pixel art” se possibile). In alternativa, icone Material con tratto `2dp` e angoli vivi.

---

## 8. Esempio di Schermata (Mockup Testuale)

**Schermata Home – Tema Scuro**

```
+------------------------------------------+
| [Logo in monospace]         [Toggle tema] |
|                                            |
|  +--------------------------------------+ |
|  |  // SURFING ACADEMY                  | |
|  |  > Scopri le onde perfette          | |
|  |  [  Prenota Ora  ]                  | |
|  +--------------------------------------+ |
|                                            |
|  [Card 1]    [Card 2]                     |
|  +--------+  +--------+                   |
|  | Titolo  |  | Titolo  |                  |
|  | testo   |  | testo   |                  |
|  +--------+  +--------+                   |
|                                            |
|  +--------------------------------------+ |
|  | "Esperienza fantastica!" – User      | |
|  +--------------------------------------+ |
|                                            |
|  [  Prenota il tuo corso  ]                |
+------------------------------------------+
```

---

## 9. Conclusioni

Il design system proposto unisce il meglio di due mondi:

- **la chiarezza e l’energia** di `surfing.academy` (tema chiaro con arancione, layout modulare, forte CTA),
- **l’audacia e il carattere tech** di `grilledpixels.com` (tema scuro, tipografia monospace, micro-interazioni, angoli vivi).

Il risultato è un’app Android nativa dall’identità visiva forte, moderna e distintiva, capace di comunicare innovazione, competenza e passione.

---

> 📄 **Versione del documento:** 1.0  
> 📅 **Data:** 2 settembre 2026  
> ✍️ **Autore:** Design System Team
