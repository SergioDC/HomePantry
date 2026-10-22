package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseCategoryTest {

    @Test fun `supermarket names and the word compra`() {
        assertEquals(ExpenseCategory.SUPERMARKET, expenseCategoryOf("Mercadona"))
        assertEquals(ExpenseCategory.SUPERMARKET, expenseCategoryOf("Compra semanal"))
        assertEquals(ExpenseCategory.SUPERMARKET, expenseCategoryOf("super del barrio"))
    }

    @Test fun `accents and case do not matter`() {
        assertEquals(ExpenseCategory.RESTAURANT, expenseCategoryOf("CENA en el Japonés"))
        assertEquals(ExpenseCategory.HEALTH, expenseCategoryOf("Médico"))
        assertEquals(ExpenseCategory.COFFEE, expenseCategoryOf("cafe"))
    }

    @Test fun `common household expenses`() {
        assertEquals(ExpenseCategory.HOTEL, expenseCategoryOf("Hotel Málaga"))
        assertEquals(ExpenseCategory.CAR, expenseCategoryOf("Gasolina"))
        assertEquals(ExpenseCategory.TRANSPORT, expenseCategoryOf("Billetes de tren"))
        assertEquals(ExpenseCategory.FLIGHT, expenseCategoryOf("Vuelo a Roma"))
        assertEquals(ExpenseCategory.HOME, expenseCategoryOf("Factura de la luz"))
        assertEquals(ExpenseCategory.LEISURE, expenseCategoryOf("Entradas cine"))
        assertEquals(ExpenseCategory.PETS, expenseCategoryOf("Veterinario"))
        assertEquals(ExpenseCategory.DRINKS, expenseCategoryOf("Unas cervezas"))
    }

    @Test fun `short keywords only match whole words`() {
        // "dia" (el súper) no debe colarse en "diario" ni "bar" en "barco".
        assertEquals(ExpenseCategory.OTHER, expenseCategoryOf("Periódico diario"))
        assertEquals(ExpenseCategory.SUPERMARKET, expenseCategoryOf("Dia"))
        assertEquals(ExpenseCategory.OTHER, expenseCategoryOf("Barco"))
        assertEquals(ExpenseCategory.RESTAURANT, expenseCategoryOf("Bar de Paco"))
    }

    @Test fun `long keywords also match as a prefix`() {
        assertEquals(ExpenseCategory.RESTAURANT, expenseCategoryOf("Restaurantes"))
        assertEquals(ExpenseCategory.HOTEL, expenseCategoryOf("Hoteles"))
    }

    @Test fun `totals by category skip reimbursements and go from largest to smallest`() {
        val expenses = listOf(
            SplitExpense(title = "Mercadona", amountCents = 3000),
            SplitExpense(title = "Cena", amountCents = 5000),
            SplitExpense(title = "Lidl", amountCents = 2500),
            SplitExpense(title = "Reembolso", amountCents = 9000, reimbursement = true)
        )
        assertEquals(
            listOf(ExpenseCategory.SUPERMARKET to 5500L, ExpenseCategory.RESTAURANT to 5000L),
            categoryTotals(expenses)
        )
        assertEquals(emptyList<Pair<ExpenseCategory, Long>>(), categoryTotals(emptyList()))
    }

    @Test fun `unknown or empty titles are OTHER`() {
        assertEquals(ExpenseCategory.OTHER, expenseCategoryOf(""))
        assertEquals(ExpenseCategory.OTHER, expenseCategoryOf("Cosas varias"))
    }
}
