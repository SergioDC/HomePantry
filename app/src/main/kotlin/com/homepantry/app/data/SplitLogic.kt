package com.homepantry.app.data

import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Importe máximo aceptado: 999.999,99 €. Evita desbordes y typos absurdos. */
const val MAX_CENTS = 99_999_999L

/** Lo pagado y la parte que le toca a un miembro. Positivo = le deben, negativo = debe. */
data class MemberBalance(val memberId: String, val paidCents: Long, val shareCents: Long) {
    val netCents: Long get() = paidCents - shareCents
}

/** [fromId] paga [amountCents] a [toId]. */
data class Settlement(val fromId: String, val toId: String, val amountCents: Long)

/**
 * Reparte [amountCents] a partes iguales. Los céntimos sobrantes van de uno en uno a los
 * primeros ids en orden alfabético: así el resultado es determinista, no depende del orden en
 * que se guardó la lista y siempre suma exactamente [amountCents]. Espera un importe >= 0.
 */
fun splitEqually(amountCents: Long, participants: List<String>): Map<String, Long> {
    val ids = participants.distinct().sorted()
    if (ids.isEmpty()) return emptyMap()
    val base = amountCents / ids.size
    val remainder = (amountCents % ids.size).toInt()
    return ids.withIndex().associate { (index, id) -> id to base + if (index < remainder) 1 else 0 }
}

/**
 * Lo que le toca a cada participante de [expense]: sus importes explícitos si los tiene y, si no
 * (todos los gastos anteriores al reparto por importes), el reparto igual de siempre.
 */
fun expenseShares(expense: SplitExpense): Map<String, Long> =
    expense.shares.ifEmpty { splitEqually(expense.amountCents, expense.participants) }

/**
 * Saldo de cada miembro. Un reembolso no necesita rama propia: quien devuelve suma lo pagado y
 * quien recibe suma su parte, igual que en un gasto normal con un único participante.
 *
 * Incluye a los miembros del mes y a cualquier uid que aparezca en un gasto (por ejemplo, un
 * dispositivo reinstalado): si se omitiera, la suma de saldos dejaría de ser 0.
 * Los gastos sin participantes o sin importe positivo se ignoran enteros, por la misma razón.
 */
fun computeBalances(expenses: List<SplitExpense>, memberIds: List<String>): List<MemberBalance> {
    val paid = HashMap<String, Long>()
    val share = HashMap<String, Long>()
    for (expense in expenses) {
        if (expense.amountCents <= 0) continue
        val shares = expenseShares(expense)
        if (shares.isEmpty()) continue
        paid[expense.payerId] = (paid[expense.payerId] ?: 0L) + expense.amountCents
        shares.forEach { (id, cents) ->
            share[id] = (share[id] ?: 0L) + cents
        }
    }
    val ids = (memberIds + paid.keys + share.keys).distinct()
    return ids
        .map { MemberBalance(it, paid[it] ?: 0L, share[it] ?: 0L) }
        .sortedWith(compareByDescending<MemberBalance> { it.netCents }.thenBy { it.memberId })
}

/**
 * Pagos para dejar todos los saldos a 0: empareja el mayor deudor con el mayor acreedor. Da como
 * mucho n-1 pagos. No siempre es el mínimo absoluto (es un problema NP-difícil), pero para un
 * grupo doméstico es más que suficiente y el resultado es determinista.
 */
fun computeSettlements(balances: List<MemberBalance>): List<Settlement> {
    val order = compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first }
    val debtors = balances.filter { it.netCents < 0 }.map { it.memberId to -it.netCents }.toMutableList()
    val creditors = balances.filter { it.netCents > 0 }.map { it.memberId to it.netCents }.toMutableList()
    val result = mutableListOf<Settlement>()
    while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
        debtors.sortWith(order)
        creditors.sortWith(order)
        val (debtorId, debt) = debtors.first()
        val (creditorId, credit) = creditors.first()
        val amount = minOf(debt, credit)
        result += Settlement(debtorId, creditorId, amount)
        if (debt == amount) debtors.removeAt(0) else debtors[0] = debtorId to debt - amount
        if (credit == amount) creditors.removeAt(0) else creditors[0] = creditorId to credit - amount
    }
    return result
}

/** Total gastado en el mes. Los reembolsos mueven dinero entre miembros, no son gasto. */
fun monthTotalCents(expenses: List<SplitExpense>): Long =
    expenses.filter { !it.reimbursement }.sumOf { it.amountCents }

/** "yyyy-MM" de [date] en [timeZone]. La zona se inyecta para que los tests no dependan del equipo. */
fun monthKeyOf(date: Date, timeZone: TimeZone = TimeZone.getDefault()): String {
    val calendar = Calendar.getInstance(timeZone).apply { time = date }
    return String.format(Locale.ROOT, "%04d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
}

fun isDateInMonth(date: Date, monthKey: String, timeZone: TimeZone = TimeZone.getDefault()): Boolean =
    monthKeyOf(date, timeZone) == monthKey

/** Campo de un gasto que no pasa la validación; la UI pinta el error bajo ese campo. */
enum class SplitExpenseError { TITLE, AMOUNT, PARTICIPANTS, PAYER, DATE, SHARES }

/**
 * Errores de un gasto antes de guardarlo; vacío = válido. Devuelve todos a la vez para que el
 * formulario los marque juntos. La fecha de un reembolso no se comprueba: se salda cuando se
 * puede, a veces ya entrado el mes siguiente.
 *
 * [shareTexts] null = reparto a partes iguales entre [participants]. Si no es null, es el
 * importe escrito para cada participante posible: entonces [participants] no se mira, participa
 * quien tenga importe > 0 y la suma tiene que ser exactamente el total.
 */
fun validateSplitExpense(
    title: String,
    amountText: String,
    payerId: String,
    participants: List<String>,
    date: Date?,
    reimbursement: Boolean,
    month: SplitMonth,
    timeZone: TimeZone = TimeZone.getDefault(),
    shareTexts: Map<String, String>? = null
): Set<SplitExpenseError> = buildSet {
    if (title.isBlank()) add(SplitExpenseError.TITLE)
    val amount = parseCents(amountText)
    if (amount == null) add(SplitExpenseError.AMOUNT)
    if (shareTexts == null) {
        if (participants.isEmpty()) add(SplitExpenseError.PARTICIPANTS)
    } else {
        val shares = parseShares(shareTexts)
        if (shares == null) {
            add(SplitExpenseError.SHARES)
        } else {
            if (shares.isEmpty()) add(SplitExpenseError.PARTICIPANTS)
            // Con el total inválido ya se marca AMOUNT: no se añade un segundo aviso por la suma.
            if (amount != null && shares.isNotEmpty() && sharesRemainderCents(amount, shares) != 0L) {
                add(SplitExpenseError.SHARES)
            }
        }
    }
    if (payerId !in month.memberIds) add(SplitExpenseError.PAYER)
    // Una actividad (un viaje, una cena) no está atada a un mes: cualquier fecha vale.
    val dateLimited = !reimbursement && !month.activity
    if (date == null || (dateLimited && !isDateInMonth(date, month.monthKey, timeZone))) {
        add(SplitExpenseError.DATE)
    }
}

/** «Mis gastos»: la suma de mis partes en los gastos del grupo. Los reembolsos no son gasto. */
fun myShareCents(expenses: List<SplitExpense>, memberId: String): Long =
    expenses.filter { !it.reimbursement && it.amountCents > 0 }
        .sumOf { expenseShares(it)[memberId] ?: 0L }

/**
 * Importe de un participante en el reparto por importes. A diferencia de [parseCents], un campo
 * en blanco o un cero valen 0 (ese miembro no participa); lo demás inválido da null.
 */
fun parseShareCents(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || ZERO_REGEX.matches(trimmed)) return 0L
    return parseCents(trimmed)
}

private val ZERO_REGEX = Regex("^0+(?:[.,]0{1,2})?$")

/**
 * Importes por participante a céntimos, solo los > 0. null si alguno no se puede leer, para que
 * el formulario lo marque en vez de tomarlo por 0 en silencio.
 */
fun parseShares(shareTexts: Map<String, String>): Map<String, Long>? {
    val result = LinkedHashMap<String, Long>()
    for ((id, text) in shareTexts) {
        val cents = parseShareCents(text) ?: return null
        if (cents > 0) result[id] = cents
    }
    return result
}

/** Lo que falta (positivo) o sobra (negativo) para que [shares] sume [totalCents]. */
fun sharesRemainderCents(totalCents: Long, shares: Map<String, Long>): Long =
    totalCents - shares.values.sum()

/**
 * Gastos agrupados por día local ("yyyy-MM-dd"), en el orden en que llegan (ya vienen del más
 * reciente al más antiguo). Los gastos sin fecha van juntos al final con la clave "".
 */
fun expensesByDay(
    expenses: List<SplitExpense>,
    timeZone: TimeZone = TimeZone.getDefault()
): List<Pair<String, List<SplitExpense>>> {
    val format = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { this.timeZone = timeZone }
    val (dated, undated) = expenses.partition { it.date != null }
    val groups = dated.groupBy { format.format(it.date!!) }.toList()
    return if (undated.isEmpty()) groups else groups + ("" to undated)
}

private val CENTS_REGEX = Regex("^(\\d{1,7})(?:[.,](\\d{1,2}))?$")

/**
 * Importe escrito por el usuario a céntimos. Acepta `12`, `12,5` y `12.50`. Rechaza vacío,
 * negativos, cero, más de dos decimales y separador de miles ("1.234,56" es ambiguo), y lo que
 * pase de [MAX_CENTS].
 */
fun parseCents(text: String): Long? {
    val match = CENTS_REGEX.matchEntire(text.trim()) ?: return null
    val euros = match.groupValues[1].toLong()
    val fraction = match.groupValues[2].padEnd(2, '0').toLong()
    val cents = euros * 100 + fraction
    return if (cents in 1..MAX_CENTS) cents else null
}
