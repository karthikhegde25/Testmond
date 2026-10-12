package com.testmond.app.ui

/**
 * A fast, WebView-free "preview" of text that contains LaTeX, for list rows (e.g. the Bookmarks
 * screen). Every `$...$` / `$$...$$` / `\(...\)` / `\[...\]` region is converted to readable
 * Unicode -- `CH_3` becomes CH₃, `x^2` becomes x², `\frac{a}{b}` becomes a/b, `\alpha` becomes α,
 * `\xrightarrow{alc. KOH}` becomes —alc. KOH→ -- instead of showing raw LaTeX, and without
 * creating one KaTeX WebView per row (which is what makes a scrolling list stutter). The exact,
 * fully typeset version is still what the question screen shows once a row is opened.
 */
internal fun mathToPlainPreview(text: String): String {
    if (!text.contains('$') && !text.contains("\\(") && !text.contains("\\[")) {
        return text.replace("\r", "").replace(Regex("\\s*\n\\s*"), " ").trim()
    }
    val src = text.replace("\r\n", "\n").replace('\r', '\n')
    val out = StringBuilder()
    val n = src.length
    var i = 0
    while (i < n) {
        val c = src[i]
        if (c == '\\' && i + 1 < n && src[i + 1] == '$') {
            out.append('$')
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
                    out.append(' ').append(latexToPlain(latex).trim()).append(' ')
                    i = end + opener.length
                    continue
                }
            }
        }
        out.append(c)
        i++
    }
    return out.toString().replace(Regex("\\s+"), " ").trim()
}

private val SUPER_MAP = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶',
    '7' to '⁷', '8' to '⁸', '9' to '⁹', '+' to '⁺', '-' to '⁻', '−' to '⁻', '=' to '⁼',
    '(' to '⁽', ')' to '⁾', 'n' to 'ⁿ', 'i' to 'ⁱ', '°' to '°'
)

private val SUB_MAP = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅', '6' to '₆',
    '7' to '₇', '8' to '₈', '9' to '₉', '+' to '₊', '-' to '₋', '−' to '₋', '=' to '₌',
    '(' to '₍', ')' to '₎', 'a' to 'ₐ', 'e' to 'ₑ', 'o' to 'ₒ', 'x' to 'ₓ', 'h' to 'ₕ',
    'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'p' to 'ₚ', 's' to 'ₛ', 't' to 'ₜ',
    'i' to 'ᵢ', 'j' to 'ⱼ'
)

private val LATEX_SYMBOLS = mapOf(
    "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε",
    "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η", "theta" to "θ", "vartheta" to "ϑ",
    "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
    "pi" to "π", "rho" to "ρ", "sigma" to "σ", "tau" to "τ", "upsilon" to "υ", "phi" to "φ",
    "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
    "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ", "Pi" to "Π",
    "Sigma" to "Σ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
    "times" to "×", "cdot" to "·", "div" to "÷", "pm" to "±", "mp" to "∓", "leq" to "≤",
    "le" to "≤", "geq" to "≥", "ge" to "≥", "neq" to "≠", "ne" to "≠", "approx" to "≈",
    "equiv" to "≡", "sim" to "∼", "propto" to "∝", "infty" to "∞",
    "to" to "→", "rightarrow" to "→", "longrightarrow" to "→", "leftarrow" to "←",
    "leftrightarrow" to "↔", "Rightarrow" to "⇒", "Leftarrow" to "⇐", "Leftrightarrow" to "⇔",
    "implies" to "⇒", "iff" to "⇔", "uparrow" to "↑", "downarrow" to "↓",
    "rightleftharpoons" to "⇌", "degree" to "°", "circ" to "°", "sum" to "∑", "prod" to "∏",
    "int" to "∫", "oint" to "∮", "partial" to "∂", "nabla" to "∇", "in" to "∈", "notin" to "∉",
    "subset" to "⊂", "supset" to "⊃", "subseteq" to "⊆", "cup" to "∪", "cap" to "∩",
    "emptyset" to "∅", "forall" to "∀", "exists" to "∃", "ldots" to "…", "dots" to "…",
    "cdots" to "…", "vdots" to "⋮", "angle" to "∠", "perp" to "⊥", "parallel" to "∥",
    "therefore" to "∴", "because" to "∵", "hbar" to "ħ", "ell" to "ℓ", "prime" to "′",
    "lbrace" to "{", "rbrace" to "}", "langle" to "⟨", "rangle" to "⟩", "mid" to "|",
    "vert" to "|", "Vert" to "‖", "triangle" to "△", "ast" to "∗", "oplus" to "⊕",
    "otimes" to "⊗", "lfloor" to "⌊", "rfloor" to "⌋", "lceil" to "⌈", "rceil" to "⌉",
    "over" to "/", "quad" to " ", "qquad" to " "
)

/** Function names that just print as themselves (\sin, \lim, ...). */
private val LATEX_WORDS = setOf(
    "sin", "cos", "tan", "cot", "sec", "csc", "log", "ln", "lim", "exp", "max", "min", "det",
    "sup", "inf", "arg", "gcd", "mod", "bmod", "pmod"
)

/** Commands that are dropped without touching what follows. */
private val LATEX_SKIP = setOf(
    "left", "right", "big", "Big", "bigg", "Bigg", "displaystyle", "textstyle",
    "scriptstyle", "limits", "nolimits", "mathstrut", "strut", "hline", "hdashline"
)

/** Commands that take one argument and print it unchanged (fonts, boxes, chemistry). */
private val LATEX_PASS_THROUGH = setOf(
    "text", "textbf", "textit", "textrm", "textsf", "texttt", "mathrm", "mathbf", "mathit",
    "mathsf", "mathtt", "mathbb", "mathcal", "mathfrak", "boldsymbol", "bm", "operatorname",
    "ce", "pu", "mbox", "boxed", "underline", "cancel", "bcancel", "xcancel", "sout",
    "substack", "mathop", "mathbin", "mathrel"
)

private val LATEX_DROP_ARG = setOf("phantom", "hphantom", "vphantom", "smash", "color")

private val MATRIX_ENVS = setOf(
    "matrix", "pmatrix", "bmatrix", "Bmatrix", "vmatrix", "Vmatrix", "smallmatrix", "array"
)

/** Reads one LaTeX argument at [from]: a {group} (without its braces), a \command, or one char. */
private fun readLatexArg(s: String, from: Int): Pair<String, Int> {
    var i = from
    while (i < s.length && s[i] == ' ') i++
    if (i >= s.length) return Pair("", i)
    val c = s[i]
    if (c == '{') {
        var depth = 0
        var j = i
        while (j < s.length) {
            val d = s[j]
            if (d == '\\') {
                j += 2
                continue
            }
            if (d == '{') depth++
            if (d == '}') {
                depth--
                if (depth == 0) return Pair(s.substring(i + 1, j), j + 1)
            }
            j++
        }
        return Pair(s.substring(i + 1), s.length)   // unbalanced: take the rest
    }
    if (c == '\\') {
        var j = i + 1
        if (j < s.length && s[j].isLetter()) {
            while (j < s.length && s[j].isLetter()) j++
        } else if (j < s.length) {
            j++
        }
        return Pair(s.substring(i, j), j)
    }
    return Pair(c.toString(), i + 1)
}

/** Reads an optional [bracket] argument at [from] (as in \sqrt[3]{x}); null if there isn't one. */
private fun readOptionalBracket(s: String, from: Int): Pair<String?, Int> {
    var i = from
    while (i < s.length && s[i] == ' ') i++
    if (i < s.length && s[i] == '[') {
        val end = s.indexOf(']', i)
        if (end > 0) return Pair(s.substring(i + 1, end), end + 1)
    }
    return Pair(null, from)
}

/** Maps every character through [map] (superscript / subscript digits); null if one can't be. */
private fun mapScript(inner: String, map: Map<Char, Char>): String? {
    val sb = StringBuilder()
    for (ch in inner) {
        val m = map[ch] ?: return null
        sb.append(m)
    }
    return sb.toString()
}

/** Wraps a multi-token expression in parentheses so "a+b over c" reads (a+b)/c. */
private fun wrapIfComposite(x: String): String {
    val t = x.trim()
    val simple = t.length <= 1 || t.all { it.isLetterOrDigit() || it == '.' }
    return if (simple) t else "($t)"
}

/** Converts the inside of one math region (no delimiters) to readable Unicode text. */
internal fun latexToPlain(latex: String): String {
    val s = latex
    val out = StringBuilder()
    var i = 0
    loop@ while (i < s.length) {
        val c = s[i]

        if (c == '\\') {
            if (i + 1 >= s.length) {
                i++
                continue@loop
            }
            val n = s[i + 1]
            if (!n.isLetter()) {
                // Control symbols: \\ \, \; \: \! \{ \} \| \% \& \_ \#
                when (n) {
                    '\\' -> out.append("; ")
                    ',', ';', ':', ' ' -> out.append(' ')
                    '!' -> {}
                    '|' -> out.append('‖')
                    else -> out.append(n)
                }
                i += 2
                continue@loop
            }
            var j = i + 1
            while (j < s.length && s[j].isLetter()) j++
            val name = s.substring(i + 1, j)
            i = j

            when {
                name == "frac" || name == "dfrac" || name == "tfrac" || name == "cfrac" -> {
                    val (a, i1) = readLatexArg(s, i)
                    val (b, i2) = readLatexArg(s, i1)
                    out.append(wrapIfComposite(latexToPlain(a))).append('/')
                        .append(wrapIfComposite(latexToPlain(b)))
                    i = i2
                }
                name == "binom" || name == "dbinom" || name == "tbinom" -> {
                    val (a, i1) = readLatexArg(s, i)
                    val (b, i2) = readLatexArg(s, i1)
                    out.append("C(").append(latexToPlain(a).trim()).append(", ")
                        .append(latexToPlain(b).trim()).append(')')
                    i = i2
                }
                name == "sqrt" -> {
                    val (index, i1) = readOptionalBracket(s, i)
                    val (arg, i2) = readLatexArg(s, i1)
                    val root = index?.trim() ?: ""
                    out.append(
                        when (root) {
                            "", "2" -> "√"
                            "3" -> "∛"
                            "4" -> "∜"
                            else -> latexToPlain(root) + "√"
                        }
                    )
                    out.append(wrapIfComposite(latexToPlain(arg)))
                    i = i2
                }
                name == "xrightarrow" || name == "xleftarrow" || name == "xrightleftharpoons" ||
                    name == "xlongequal" -> {
                    val (below, i1) = readOptionalBracket(s, i)
                    val (arg, i2) = readLatexArg(s, i1)
                    val label = latexToPlain(arg).trim()
                    val under = if (below != null) latexToPlain(below).trim() else ""
                    val text = listOf(label, under).filter { it.isNotEmpty() }.joinToString(" / ")
                    val head = if (name == "xleftarrow") "←" else if (name == "xrightleftharpoons") "⇌" else "→"
                    out.append(if (text.isEmpty()) head else " —$text$head ")
                    i = i2
                }
                name == "vec" || name == "overrightarrow" || name == "hat" || name == "widehat" ||
                    name == "bar" || name == "overline" || name == "dot" || name == "ddot" ||
                    name == "tilde" || name == "widetilde" -> {
                    val (arg, i1) = readLatexArg(s, i)
                    val mark = when (name) {
                        "vec", "overrightarrow" -> "\u20D7"
                        "hat", "widehat" -> "\u0302"
                        "bar", "overline" -> "\u0304"
                        "dot" -> "\u0307"
                        "ddot" -> "\u0308"
                        else -> "\u0303"
                    }
                    out.append(latexToPlain(arg)).append(mark)
                    i = i1
                }
                name == "overset" || name == "underset" || name == "stackrel" -> {
                    val (_, i1) = readLatexArg(s, i)
                    val (base, i2) = readLatexArg(s, i1)
                    out.append(latexToPlain(base))
                    i = i2
                }
                name == "textcolor" || name == "colorbox" -> {
                    val (_, i1) = readLatexArg(s, i)
                    val (arg, i2) = readLatexArg(s, i1)
                    out.append(latexToPlain(arg))
                    i = i2
                }
                name == "begin" -> {
                    val (env, i1) = readLatexArg(s, i)
                    i = i1
                    if (env == "array") i = readLatexArg(s, i).second   // column spec like {c|c}
                    if (env in MATRIX_ENVS) out.append("[ ") else if (env == "cases") out.append("{ ")
                }
                name == "end" -> {
                    val (env, i1) = readLatexArg(s, i)
                    i = i1
                    if (env in MATRIX_ENVS) out.append(" ]") else if (env == "cases") out.append(" }")
                }
                name in LATEX_PASS_THROUGH -> {
                    val (arg, i1) = readLatexArg(s, i)
                    out.append(latexToPlain(arg))
                    i = i1
                }
                name in LATEX_DROP_ARG -> {
                    i = readLatexArg(s, i).second
                }
                name in LATEX_SKIP -> {
                    // \left. and \right. are invisible delimiters.
                    if ((name == "left" || name == "right") && i < s.length && s[i] == '.') i++
                }
                name in LATEX_WORDS -> out.append(name).append(' ')
                LATEX_SYMBOLS.containsKey(name) -> out.append(LATEX_SYMBOLS[name])
                else -> out.append(name)
            }
            continue@loop
        }

        if (c == '^' || c == '_') {
            val (arg, next) = readLatexArg(s, i + 1)
            i = next
            val inner = latexToPlain(arg).trim()
            val mapped = mapScript(inner, if (c == '^') SUPER_MAP else SUB_MAP)
            if (mapped != null) {
                out.append(mapped)
            } else {
                out.append(c).append(wrapIfComposite(inner))
            }
            continue@loop
        }

        when (c) {
            '{', '}' -> {}
            '~' -> out.append(' ')
            '&' -> out.append(' ')
            else -> out.append(c)
        }
        i++
    }
    return out.toString().replace(Regex("\\s+"), " ").trim()
}
