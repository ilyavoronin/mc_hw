import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class EvalTests {

    @Test
    fun testPlus() {
        val expr = Plus(Plus(Value(5.0), Variable("x")), Value(3.0))

        val expected = 10.5
        val actual = eval(expr, mapOf(Pair("x", 2.5)))

        assertEquals(expected, actual)
    }

    @Test
    fun testDiffPow() {
        val expr = Pow(Variable("x"), Variable("x"))

        val expected = Multiply(Pow(Variable("x"), Variable("x")), Plus(Value(1.0), Ln(Variable("x"))))
        val actual = diff(expr, "x")
        println(Plus(Value(1.0), Value(2.0)) == Plus(Value(1.0), Value(2.0)))
        assertEquals(expected, actual)
    }

    @Test
    fun simpleDiffPow() {
        val expr = Pow(Variable("x"), Variable("y"))

        val expected = Multiply(Variable("y"), Pow(Variable("x"), Minus(Variable("y"), Value(1.0))))
        val actual = diff(expr, "x")

        assertEquals(expected, actual)
    }

    @Test
    fun deleteZerosAndOnes() {
        val expr = Plus(
            Multiply(Variable("x"), Value(5.0)),
            Value(7.0)
        )

        val expected = Value(5.0)
        val actual = diff(expr, "x")

        assertEquals(expected, actual)
    }
}