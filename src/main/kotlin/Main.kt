import java.lang.IllegalStateException
import kotlin.math.*

sealed class TwoOps<T>: Expr() {
    abstract val x: T
    abstract val y: T
    final override fun equals(other: Any?): Boolean {
        return other is TwoOps<*> && (x == other.x && y == other.y || x == other.y && y == other.x)
    }
}

sealed class Expr
data class Value(val value: Double): Expr()
data class Variable(val name: String): Expr()
data class Plus(override val x: Expr, override val y: Expr): TwoOps<Expr>()
data class Minus(val x: Expr, val y: Expr): Expr()
data class Multiply(override val x: Expr, override val y: Expr): TwoOps<Expr>()
data class Divide(val x: Expr, val y: Expr): Expr()
data class Sin(val expr: Expr): Expr()
data class Cos(val expr: Expr): Expr()
data class Ln(val expr: Expr): Expr()
data class Pow(val expr: Expr, val p: Expr): Expr()

fun eval(expr: Expr, env: Map<String, Double>): Double {
    return when (expr) {
        is Value -> expr.value
        is Variable -> env[expr.name] ?: throw IllegalStateException("There is no variable ${expr.name} in the context")
        is Plus -> eval(expr.x, env) + eval(expr.y, env)
        is Minus -> eval(expr.x, env) - eval(expr.y, env)
        is Multiply -> eval(expr.x, env) * eval(expr.y, env)
        is Divide -> eval(expr.x, env) / eval(expr.y, env)
        is Sin -> sin(eval(expr.expr, env))
        is Cos -> cos(eval(expr.expr, env))
        is Ln -> ln(eval(expr.expr, env))
        is Pow -> {
            val a = eval(expr.expr, env)
            val p = eval(expr.p, env)
            a.pow(p)
        }
    }
}

fun diff(expr: Expr, variable: String): Expr {
    fun diff_(expr: Expr, variable: String): Expr {
        return when (expr) {
            is Value -> Value(0.0)
            is Variable -> if (expr.name == variable) Value(1.0) else Value(0.0)
            is Plus -> Plus(diff_(expr.x, variable), diff_(expr.y, variable))
            is Minus -> Minus(diff_(expr.x, variable), diff_(expr.y, variable))
            is Multiply -> Plus(
                Multiply(diff_(expr.x, variable), expr.y),
                Multiply(expr.x, diff_(expr.y, variable))
            )
            is Divide -> Divide(
                Minus(
                    Multiply(diff_(expr.x, variable), expr.y),
                    Multiply(expr.x, diff_(expr.y, variable))
                ),
                Pow(expr.y, Value(2.0))
            )
            is Sin -> Multiply(
                diff_(expr.expr, variable),
                Cos(expr.expr)
            )
            is Cos -> Multiply(
                Value(-1.0),
                Multiply(
                    diff_(expr.expr, variable),
                    Sin(expr.expr)
                )
            )
            is Ln -> Divide(
                diff_(expr.expr, variable),
                expr.expr
            )
            is Pow -> Multiply(
                expr,
                Plus(
                    Multiply(
                        diff_(expr.p, variable),
                        Ln(expr.expr)
                    ),
                    Multiply(
                        expr.p,
                        diff_(Ln(expr.expr), variable)
                    )
                )
            )
        }
    }

    return transform(diff_(expr, variable))
}

fun Expr.t(): Expr {
    return transform(this)
}

fun reducex(expr: Expr, x: Variable): Expr? {
    return when {
        expr is Multiply -> {
            val red1 = reducex(expr.x, x)
            if (red1 != null) {
                Multiply(red1, expr.y).t()
            } else {
                val red2 = reducex(expr.y, x) ?: expr.y
                Multiply(expr.x, red2).t()
            }
        }
        expr is Plus -> {
            val red1 = reducex(expr.x, x) ?: expr.x
            val red2 = reducex(expr.y, x) ?: expr.y
            Plus(red1, red2).t()
        }
        expr is Variable && expr.name == x.name -> Value(1.0)
        expr is Pow && expr.expr == x -> Pow(x, Minus(expr.p, Value(1.0)).t()).t()
        else -> null
    }
}

fun transform(expr: Expr): Expr {
    fun isZero(expr: Expr): Boolean {
        return expr is Value && expr.value == 0.0
    }

    fun isOne(expr: Expr): Boolean {
        return expr is Value && expr.value == 1.0
    }

    try {
        return Value(eval(expr, emptyMap()))
    } catch (e: IllegalStateException) { }

    return when(expr) {
        is Value -> expr
        is Variable -> expr
        is Plus -> {
            val tx = transform(expr.x)
            val ty = transform(expr.y)
            when {
                isZero(tx) -> ty
                isZero(ty) -> tx
                else -> Plus(tx, ty)
            }
        }
        is Minus -> {
            val tx = expr.x.t()
            val ty = expr.y.t()
            when {
                isZero(ty) -> tx
                isZero(tx) -> Multiply(Value(-1.0), ty)
                else -> expr
            }
        }
        is Multiply -> {
            val tx = transform(expr.x)
            val ty = transform(expr.y)
            when {
                isZero(tx) || isZero(ty) -> Value(0.0)
                isOne(tx) -> ty
                isOne(ty) -> tx
                tx is Divide && ty is Divide -> Divide(Multiply(tx.x, ty.x).t(), Multiply(tx.y, ty.y).t()).t()
                tx is Divide -> Divide(Multiply(tx.x, ty).t(), tx.y).t()
                ty is Divide -> Divide(Multiply(tx, ty.x).t(), ty.y).t()
                tx is Variable && ty is Variable && tx.name == ty.name -> Pow(Variable(tx.name), Value(2.0))
                else -> Multiply(tx, ty)
            }
        }
        is Divide -> {
            val tx = transform(expr.x)
            val ty = transform(expr.y)
            when {
                isZero(tx) -> Value(0.0)
                isOne(ty) -> tx
                tx is Divide && ty is Divide -> Divide(Multiply(tx.x, ty.y).t(), Multiply(tx.y, ty.x).t()).t()
                tx is Divide -> Divide(tx.x, Multiply(tx.y, ty).t()).t()
                ty is Divide -> Divide(Multiply(tx, ty.y).t(), tx).t()
                ty is Variable -> reducex(tx, ty) ?: expr
                tx == ty -> Value(1.0)
                else -> Divide(tx, ty)
            }
        }
        is Sin -> Sin(
            transform(expr.expr)
        )
        is Cos -> Cos(
            transform(expr.expr)
        )
        is Ln -> Ln(
            transform(expr.expr)
        )
        is Pow -> {
            val a = transform(expr.expr)
            val p = transform(expr.p)

            when {
                isZero(a) -> Value(0.0)
                isZero(p) -> Value(1.0)
                isOne(a) -> Value(1.0)
                isOne(p) -> a
                else -> Pow(
                    a,
                    p
                )
            }
        }
    }
}

fun main() {
    val expr = Value(0.0)
    val env = mapOf<String, Double>()

    println(
        diff(expr, "x")
    )

    println(
        eval(expr, env)
    )
}