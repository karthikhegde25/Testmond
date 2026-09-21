package com.testmond.app.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private val MATH_DELIMITER = Regex("""\$\$|\$|\\\(|\\\[""")

private fun escapeHtml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/**
 * Finds where the math that opened with [opener] ends, starting the search at [from]. Returns the
 * index of the closing delimiter, or -1 if there isn't one. A backslash escapes the next
 * character (so `\\`, `\$`, `\{` inside math never close it) -- except when it is the start
 * of the closing `\]` / `\)` itself.
 */
private fun findMathEnd(text: String, from: Int, opener: String): Int {
    val n = text.length
    var j = from
    while (j < n) {
        val c = text[j]
        if (c == '\\') {
            if (opener == "\\[" && j + 1 < n && text[j + 1] == ']') return j
            if (opener == "\\(" && j + 1 < n && text[j + 1] == ')') return j
            j += 2
            continue
        }
        if (c == '$') {
            if (opener == "\$\$") {
                if (j + 1 < n && text[j + 1] == '$') return j
            } else if (opener == "\$") {
                return j
            }
        }
        j++
    }
    return -1
}

// ---------- Markdown tables ----------
//
// A pipe table in question text is drawn as a real HTML table that always fits the screen width:
// columns share the width, long cell text wraps (cells may contain $...$ math too), and if a table
// is still too wide because of an unbreakable formula the page scales it down to fit (the WebView
// can't be scrolled sideways). Written the usual way:
//
//     | Trial | [A] | Rate |
//     |:--|:-:|--:|
//     | 1 | 0.10 | $2.0\times10^{-3}$ |
//
// A table needs its header row followed by the |---|---| separator row (":" marks alignment), and
// runs until a blank line or a line without a pipe.

private val TABLE_SEPARATOR = Regex("^\\s*\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?\\s*$")
private val TABLE_PLACEHOLDER = Regex("(?:<br>)?\u0002T(\\d+)\u0002(?:<br>)?")

private fun isTableSeparator(line: String): Boolean = line.contains('|') && TABLE_SEPARATOR.matches(line)

/** Splits one table row into cells. A pipe inside $...$ math or written \\| is not a separator. */
private fun splitTableRow(line: String): List<String> {
    var s = line.trim()
    if (s.startsWith("|")) s = s.substring(1)
    if (s.endsWith("|") && !s.endsWith("\\|")) s = s.substring(0, s.length - 1)
    val cells = ArrayList<String>()
    val cell = StringBuilder()
    var inMath = false
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length) {
            val next = s[i + 1]
            // \\| outside math is a literal pipe; inside math it is LaTeX's double bar, so keep it.
            if (next == '|' && !inMath) cell.append('|') else cell.append(c).append(next)
            i += 2
            continue
        }
        if (c == '$') inMath = !inMath
        if (c == '|' && !inMath) {
            cells.add(cell.toString().trim())
            cell.setLength(0)
        } else {
            cell.append(c)
        }
        i++
    }
    cells.add(cell.toString().trim())
    return cells
}

private fun tableAlign(separatorCell: String): String {
    val c = separatorCell.trim()
    return when {
        c.startsWith(":") && c.endsWith(":") -> "center"
        c.endsWith(":") -> "right"
        else -> "left"
    }
}

private fun renderTable(header: List<String>, aligns: List<String>, rows: List<List<String>>): String {
    val sb = StringBuilder("<div class=\"tmtable\"><table><thead><tr>")
    header.forEachIndexed { i, cell ->
        sb.append("<th style=\"text-align:").append(aligns[i]).append("\">").append(toMathHtml(cell)).append("</th>")
    }
    sb.append("</tr></thead><tbody>")
    for (row in rows) {
        sb.append("<tr>")
        for (i in header.indices) {
            sb.append("<td style=\"text-align:").append(aligns[i]).append("\">")
                .append(toMathHtml(row.getOrElse(i) { "" })).append("</td>")
        }
        sb.append("</tr>")
    }
    sb.append("</tbody></table></div>")
    return sb.toString()
}

/** Replaces each table in [src] with a placeholder line and stores its finished HTML in [tables]. */
private fun extractTables(src: String, tables: MutableList<String>): String {
    if (!src.contains('|')) return src
    val lines = src.split("\n")
    val out = ArrayList<String>(lines.size)
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (i + 1 < lines.size && line.contains('|') && isTableSeparator(lines[i + 1])) {
            val header = splitTableRow(line)
            val separator = splitTableRow(lines[i + 1])
            if (header.size == separator.size) {
                var j = i + 2
                val rows = ArrayList<List<String>>()
                while (j < lines.size && lines[j].isNotBlank() && lines[j].contains('|')) {
                    rows.add(splitTableRow(lines[j]))
                    j++
                }
                tables.add(renderTable(header, separator.map { tableAlign(it) }, rows))
                out.add("\u0002T${tables.size - 1}\u0002")
                i = j
                continue
            }
        }
        out.add(line)
        i++
    }
    return out.joinToString("\n")
}

/** True when [text] contains a pipe table (so it must go through the WebView even with no math). */
internal fun hasMarkdownTable(text: String): Boolean {
    if (!text.contains('|') || !text.contains("---")) return false
    val lines = text.split("\n")
    for (i in 0 until lines.size - 1) {
        if (lines[i].contains('|') && isTableSeparator(lines[i + 1]) &&
            splitTableRow(lines[i]).size == splitTableRow(lines[i + 1]).size
        ) return true
    }
    return false
}

/**
 * Turns question text into the HTML that goes inside the WebView: ordinary text is HTML-escaped
 * with line breaks kept as <br>, and every math region ($...$, $$...$$, \(...\), \[...\]) is
 * wrapped in `<span class="tm" data-display="0|1">` holding its raw LaTeX for KaTeX to render.
 *
 * Splitting the text here (instead of leaving it to KaTeX's auto-render) matters: auto-render
 * only recognises a delimiter pair inside ONE text node, so a formula that spans several lines
 * -- a matrix, an aligned block, anything pasted with line breaks -- was cut into pieces by the
 * <br> elements and never rendered. Line breaks INSIDE math stay as plain whitespace in the
 * LaTeX (which is what LaTeX itself does), and only line breaks in ordinary text become <br>.
 */
internal fun toMathHtml(text: String): String {
    val tables = ArrayList<String>()
    val src = extractTables(text.replace("\r\n", "\n").replace("\r", "\n"), tables)
    val out = StringBuilder()
    val plain = StringBuilder()
    var dropNextNewline = false   // swallow the line break that directly follows a display formula

    fun flushPlain() {
        if (plain.isNotEmpty()) {
            out.append(escapeHtml(plain.toString()).replace("\n", "<br>"))
            plain.clear()
        }
    }

    var i = 0
    val n = src.length
    while (i < n) {
        val c = src[i]

        // \$ is a literal dollar sign, not a math delimiter.
        if (c == '\\' && i + 1 < n && src[i + 1] == '$') {
            plain.append('$')
            dropNextNewline = false
            i += 2
            continue
        }

        val opener: String? = when {
            src.startsWith("\$\$", i) -> "\$\$"
            src.startsWith("\\[", i) -> "\\["
            src.startsWith("\\(", i) -> "\\("
            c == '$' -> "\$"
            else -> null
        }
        if (opener != null) {
            val end = findMathEnd(src, i + opener.length, opener)
            if (end >= 0) {
                val latex = src.substring(i + opener.length, end)
                if (latex.isNotBlank()) {
                    val display = opener == "\$\$" || opener == "\\["
                    if (display && plain.endsWith("\n")) plain.setLength(plain.length - 1)
                    flushPlain()
                    out.append("<span class=\"tm\" data-display=\"").append(if (display) "1" else "0").append("\">")
                    out.append(escapeHtml(latex))
                    out.append("</span>")
                    dropNextNewline = display
                    i = end + opener.length
                    continue
                }
            }
        }

        if (dropNextNewline && c == '\n') {
            dropNextNewline = false
            i++
            continue
        }
        dropNextNewline = false
        plain.append(c)
        i++
    }
    flushPlain()
    val html = out.toString()
    if (tables.isEmpty()) return html
    // Put each finished table where its placeholder line was (and drop the <br> hugging it).
    return TABLE_PLACEHOLDER.replace(html) { m -> tables[m.groupValues[1].toInt()] }
}

/** Plain mutable box (not a MutableState) used to bridge "current" values into
 *  WebViewClient callbacks that were created once inside AndroidView's factory.
 *  factory only ever runs once per screen position; if the same underlying WebView
 *  later gets reused for different content (e.g. list recycling, or the composable's
 *  text argument simply changing), closures captured directly at factory-time would
 *  keep referencing that first invocation's text/state forever. Keeping the box's
 *  identity stable via remember{} while refreshing its .value every recomposition
 *  (in the update block) lets those same closures always read the current value. */
private class LiveRef<T>(var value: T)

/** Lets the page tell Kotlin its content height the moment it changes (fonts arriving, KaTeX
 *  finishing, a reflow...) instead of relying only on timed polling. JavaScript calls arrive on
 *  a background thread, hence the post. */
private class HeightBridge(private val view: WebView, private val onPx: (Float) -> Unit) {
    @JavascriptInterface
    fun onHeight(px: Double) {
        view.post { onPx(px.toFloat()) }
    }
}

/**
 * Renders question/option/answer text. Plain strings render as a normal Text
 * composable (fast path, the common case). Strings containing a LaTeX delimiter
 * ($...$, $$...$$, \(...\), \[...\]) render through KaTeX inside a small WebView.
 *
 * KaTeX (JS, CSS, and all fonts) is fully bundled in app/src/main/assets/katex/ --
 * no network required, ever.
 *
 * Rendering and sizing are both driven from the Kotlin side via evaluateJavascript,
 * triggered by WebViewClient.onPageFinished -- a callback the WebView platform
 * guarantees fires regardless of whether any in-page JavaScript behaves as expected.
 */
@Composable
fun MathAwareText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
) {
    if (!MATH_DELIMITER.containsMatchIn(text) && !hasMarkdownTable(text)) {
        Text(text, modifier = modifier, style = style)
    } else {
        MathText(text, modifier = modifier, fontSizeSp = style.fontSize.value.let { if (it > 0) it.toInt() else 16 })
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MathText(
    text: String,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 16
) {
    val textColorArgb = LocalContentColor.current.toArgb()
    // No upper bound -- the box grows to fit however much content KaTeX renders.
    var heightDp by remember(text) { mutableStateOf(24.dp) }

    // Stable indirection so the WebViewClient (created once, in factory) always reads
    // the CURRENT height-setter, not whatever it was the first time this screen
    // position's WebView was created. See LiveRef doc above.
    val heightSetterRef = remember { LiveRef<(Dp) -> Unit>({}) }
    heightSetterRef.value = { newHeight -> heightDp = newHeight }

    // AndroidView's update block runs on EVERY recomposition that touches this composable,
    // not only when text/color/size actually changed -- e.g. tapping an option anywhere
    // on screen recomposes this whole area. Tracking what was last actually loaded and
    // skipping a no-op reload avoids reloading every math WebView from scratch on each
    // unrelated recomposition.
    val lastLoadedKeyRef = remember { LiveRef<String?>(null) }

    // Renders every <span class="tm"> (built by toMathHtml) with KaTeX, retrying briefly in case
    // katex.min.js hasn't finished executing yet (it's a plain synchronous <script src>, so this
    // is a safety net). Afterwards, any display formula wider than the screen is scaled down to
    // fit -- the WebView swallows touch, so a wide matrix could otherwise never be scrolled into
    // view. (There is deliberately no dollar sign anywhere in this script: it sits inside a
    // Kotlin raw string.)
    val renderJs = """
        (function() {
          function fitDisplays() {
            var avail = document.documentElement.clientWidth || window.innerWidth;
            var list = document.querySelectorAll('.katex-display > .katex');
            for (var i = 0; i < list.length; i++) {
              var k = list[i];
              k.style.zoom = '';
              var w = k.scrollWidth;
              if (avail > 0 && w > avail) { k.style.zoom = String((avail - 2) / w); }
            }
            var tables = document.querySelectorAll('.tmtable > table');
            for (var j = 0; j < tables.length; j++) {
              var t = tables[j];
              t.style.zoom = '';
              t.classList.remove('tight');
              var tw = t.scrollWidth;
              if (avail > 0 && tw > avail) {
                // Scaling a very wide table down to fit would make it unreadably small, so below
                // ~65% use tighter text and let cells break inside words instead, and scale only
                // what still overflows.
                if ((avail - 2) / tw < 0.65) { t.classList.add('tight'); tw = t.scrollWidth; }
                if (tw > avail) { t.style.zoom = String((avail - 2) / tw); }
              }
            }
          }
          function render() {
            var nodes = document.querySelectorAll('span.tm');
            for (var i = 0; i < nodes.length; i++) {
              var el = nodes[i];
              try {
                katex.render(el.textContent, el, {
                  displayMode: el.getAttribute('data-display') === '1',
                  throwOnError: false
                });
              } catch (e) {}
            }
            fitDisplays();
            if (document.fonts && document.fonts.ready) { document.fonts.ready.then(fitDisplays); }
            setTimeout(fitDisplays, 300);
            setTimeout(fitDisplays, 1000);
            // Push the height to Kotlin whenever the content's size changes.
            if (window.TestmondBridge && window.ResizeObserver) {
              var report = function() {
                try { window.TestmondBridge.onHeight(tmHeight()); } catch (e) {}
              };
              new ResizeObserver(report).observe(document.getElementById('content'));
              report();
            }
          }
          function attempt(retriesLeft) {
            if (typeof katex !== 'undefined' && katex.render) {
              render();
            } else if (retriesLeft > 0) {
              setTimeout(function() { attempt(retriesLeft - 1); }, 150);
            }
          }
          attempt(25);
        })();
    """.trimIndent()

    AndroidView(
        modifier = modifier.fillMaxWidth().height(heightDp),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(0x00000000)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setSupportZoom(false)
                // Static display surface only -- swallow touch so it doesn't
                // intercept scrolling/zooming inside a scrollable parent.
                setOnTouchListener { _, _ -> true }

                fun applyHeight(px: Float) {
                    if (px <= 0f) return
                    // CSS px maps 1:1 to Android dp, given the "width=device-width,
                    // initial-scale=1.0" viewport meta tag below.
                    val dp = px.dp
                    heightSetterRef.value(if (dp < 20.dp) 20.dp else dp)
                }

                fun measureHeight(view: WebView) {
                    // tmHeight() (defined in the page) measures the real extent of everything
                    // that was drawn -- including tall fractions / superscripts that stick out
                    // above the first line and display-math margins -- which
                    // document.body.scrollHeight misses.
                    view.evaluateJavascript("typeof tmHeight === 'function' ? String(tmHeight()) : '0'") { result ->
                        val px = result?.trim('"')?.toFloatOrNull() ?: return@evaluateJavascript
                        applyHeight(px)
                    }
                }

                addJavascriptInterface(HeightBridge(this) { px -> applyHeight(px) }, "TestmondBridge")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val v = view ?: return
                        v.evaluateJavascript(renderJs, null)
                        // Poll a handful of times to catch layout settling after render --
                        // KaTeX's DOM changes, web font swap-in, and any late reflow all
                        // land within this window without needing the page to report back.
                        listOf(0L, 100L, 250L, 500L, 900L, 1500L, 2500L, 4000L, 7000L).forEach { delayMs ->
                            v.postDelayed({ measureHeight(v) }, delayMs)
                        }
                    }
                }
            }
        },
        update = { webView ->
            val colorHex = String.format("#%06X", 0xFFFFFF and textColorArgb)
            val contentHtml = toMathHtml(text)

            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                <link rel="stylesheet" href="katex.min.css">
                <script src="katex.min.js"></script>
                <script>
                  // Real height of the drawn content. Anything that sticks out ABOVE the top edge
                  // (a tall fraction, a superscript on a big symbol) would be clipped by the
                  // WebView and can't be scrolled to, so the content is pushed down by exactly
                  // that much; the total then covers everything down to the lowest pixel.
                  function tmHeight() {
                    var c = document.getElementById('content');
                    if (!c) { return 0; }
                    var base = c.getBoundingClientRect();
                    var minTop = base.top;
                    var maxBottom = base.bottom;
                    var els = c.getElementsByTagName('*');
                    for (var i = 0; i < els.length; i++) {
                      var r = els[i].getBoundingClientRect();
                      if (r.width > 0 && r.height > 0) {
                        if (r.top < minTop) { minTop = r.top; }
                        if (r.bottom > maxBottom) { maxBottom = r.bottom; }
                      }
                    }
                    if (minTop < base.top - 0.5) {
                      var extra = base.top - minTop + 1;
                      var pad = parseFloat(window.getComputedStyle(c).paddingTop) || 0;
                      c.style.paddingTop = (pad + extra) + 'px';
                      maxBottom += extra;
                    }
                    window.scrollTo(0, 0);
                    return Math.ceil(maxBottom) + 1;
                  }
                </script>
                <style>
                  /* overflow-anchor: none stops the browser from scrolling the page to "keep its
                     place" while KaTeX grows the content above it (which left the top of a
                     question cut off), and the page itself never scrolls. */
                  html, body { margin:0; padding:0; background: transparent; overflow: hidden; overflow-anchor: none; }
                  /* Markdown tables: full width, cells wrap; too-wide ones are scaled by the script. */
                  .tmtable { margin: 6px 0; }
                  .tmtable table { border-collapse: collapse; width: 100%; font-size: 0.94em; }
                  .tmtable th, .tmtable td {
                    border: 1px solid rgba(128,128,128,0.55);
                    padding: 4px 6px;
                    vertical-align: top;
                    overflow-wrap: break-word;
                  }
                  .tmtable th { background: rgba(128,128,128,0.18); font-weight: 600; }
                  /* Only for very wide tables (see the script): allow breaking inside words. */
                  .tmtable table.tight { font-size: 0.86em; }
                  .tmtable table.tight th, .tmtable table.tight td { overflow-wrap: anywhere; padding: 3px 4px; }
                  /* flow-root keeps display-math margins inside the measured box. */
                  #content { display: flow-root; padding: 2px 0; }
                  body {
                    font-family: sans-serif;
                    font-size: ${fontSizeSp}px;
                    color: $colorHex;
                    word-wrap: break-word;
                    overflow-wrap: break-word;
                  }
                </style>
                </head>
                <body>
                <div id="content">$contentHtml</div>
                </body>
                </html>
            """.trimIndent()

            // Base URL points at the bundled assets/katex/ folder -- the CSS, fonts and
            // katex.min.js all resolve locally. No network access needed at all.
            val loadKey = "$text|$fontSizeSp|$colorHex"
            if (lastLoadedKeyRef.value != loadKey) {
                lastLoadedKeyRef.value = loadKey
                webView.loadDataWithBaseURL(
                    "file:///android_asset/katex/",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    )
}
