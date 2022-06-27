package calculator

import java.math.BigInteger

object Calculator {
    private val commandRegex = "/.*".toRegex()
    private val assignmentRegex = ".*=.*".toRegex()
    private val identifierRegex = "[a-zA-Z]+".toRegex()
    private var isOn = true
    private val variables = mutableMapOf<String, BigInteger>()

    init {
        while (isOn) {
        val input = readln()
            try {
                when {
                    input.isBlank() -> continue
                    input.matches(commandRegex) -> executeCommand(input)
                    input.matches(assignmentRegex) -> executeAssignment(input)
                    else -> showResult(input)
                }
            } catch (e: RuntimeException) {
                println(e.message)
            }
        }
    }

    private fun executeCommand(command: String) {
        when (command) {
            "/exit" -> {
                println("Bye!")
                isOn = false
            }
            "/help" -> {
                println("This program is a smart calculator.")
                println("You can use big integers, +, -, *, /, ^, parentheses and variables.")
            }
            else -> throw(RuntimeException("Unknown command"))
        }
    }

    private fun executeAssignment(assignment: String) {
        val (name, expression) = assignment.trim().split("""\s*=\s*""".toRegex(), limit = 2)
        require(name.matches(identifierRegex)) { "Invalid identifier" }
        try {
            variables[name] = Expression(expression).value
        } catch (e: IllegalArgumentException) {
            throw(RuntimeException("Invalid assignment"))
        }
    }

    private fun showResult(input: String) {
        try {
            println(Expression(input).value)
        } catch (e: IllegalArgumentException) {
            throw(RuntimeException("Invalid expression"))
        }
    }

    class Expression(input: String) {

        enum class Operator(
            val symbol: String,
            val precedence: Int,
            val perform: (BigInteger, BigInteger) -> BigInteger,
            val rtl: Boolean = false,
            val unary: Boolean = false,
        ) {
            MINUS("-", 0, { a, b -> a - b }),
            PLUS("+", 0, { a, b -> a + b }),
            TIMES("*", 1, { a, b -> a * b }),
            DIVIDE("/", 1, { a, b ->
                require(b != BigInteger.ZERO) { "Division by zero" }
                a / b }),
            POWER("^", 2, { a, b ->
                require(b >= BigInteger.ZERO) { "Negative exponent" }
                require(b < Int.MAX_VALUE.toBigInteger()) { "Exponent is too big" }
                a.pow(b.toInt()) }, rtl = true),
            NEGATE("~", 2, { a, _ -> -a }, rtl = true, unary = true); // unary minus

            companion object {
                val operators = buildList { values().forEach { add(it.symbol) } } // list with all operator symbols
                private val mapBySymbol: Map<String, Operator> = buildMap { values().forEach { put(it.symbol, it) } }
                fun bySymbol(symbol: String): Operator = mapBySymbol[symbol]!! // returns an operator by its symbol
            }
        }

        companion object {
            private val expressionRegex = "[${Operator.operators.joinToString("")}()\\w\\s]+".toRegex()
        }

        private val infixNotation: List<String> = if (input.matches(expressionRegex)) {
            input.replace("""\s+""".toRegex(), "") // remove whitespace
                .replace("--", "+") // remove double negation
                .replace("""((?<=[-(^+])\++|^\++|\++(?=-))""".toRegex(), "") // remove unary plus
                .replace("""((?<=[(^])-|^-)""".toRegex(), "~") // change unary minus to ~
                .replace("""\W""".toRegex(), " $0 ").trim().split("""\s+""".toRegex()) // make list of elements
            } else emptyList()

        private val postfixNotation: List<String> = buildList {
            try {
                val stack = ArrayDeque<String>()
                infixNotation.forEachIndexed { index, s ->
                    val hasLeftOperand = (index > 0)
                            && (infixNotation[index - 1] !in Operator.operators)
                            && (infixNotation[index - 1] != "(")
                    when (s) {
                        "(" -> {
                            require(!hasLeftOperand) // require "(" is first or after "(" or after an operator
                            stack.addFirst(s)
                        }
                        ")" -> {
                            require(hasLeftOperand) // require ")" isn't first or after "(" or after an operator
                            while (stack.isNotEmpty() && stack.first() != "(") add(stack.removeFirst())
                            require(stack.removeFirst() == "(") // check parentheses matching and discard parentheses
                        }
                        in Operator.operators -> {
                            val op1 = Operator.bySymbol(s)
                            require(op1.unary xor hasLeftOperand) // require left operand only for binary operators
                            while (stack.isNotEmpty() && stack.first() != "(") {
                                val op2 = Operator.bySymbol(stack.first())
                                if (op2.precedence < op1.precedence) break
                                if (op2.precedence == op1.precedence && op1.rtl) break
                                add(stack.removeFirst())
                            }
                            stack.addFirst(s)
                        }
                        else -> { // numbers and variables
                            require(!hasLeftOperand) // require ")" isn't before numbers and variables
                            add(s)
                        }
                    }
                }
                while (stack.isNotEmpty()) {
                    require(stack.first() != "(") // check parentheses matching
                    add(stack.removeFirst())
                }
            } catch (e: RuntimeException) { // return empty list in case of illegal expression
                clear()
                return@buildList
            }
        }

        val value: BigInteger // evaluate the expression
            get() {
                val stack = ArrayDeque<BigInteger>()
                postfixNotation.forEach {
                    if (it in Operator.operators) { // an operator
                        val op = Operator.bySymbol(it)
                        if (op.unary) { // unary operator
                            require(stack.isNotEmpty())
                            stack.addFirst(op.perform(stack.removeFirst(), BigInteger.ZERO))
                        } else { // binary operator
                            require(stack.size >= 2)
                            val b = stack.removeFirst()
                            val a = stack.removeFirst()
                            stack.addFirst(op.perform(a, b))
                        }
                    } else { // numbers and variables
                        stack.addFirst(
                            if (it.matches(identifierRegex)) {
                                variables[it] ?: throw (RuntimeException("Unknown variable"))
                            } else it.toBigInteger()
                        )
                    }
                }
                require(stack.isNotEmpty())
                return stack.first()
            }
    }
}

fun main () {
    Calculator
}
